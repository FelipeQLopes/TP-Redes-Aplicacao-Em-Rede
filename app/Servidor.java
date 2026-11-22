import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SERVIDOR (roda no PC2)
 *
 * - Thread "tcp-accept": aceita conexões TCP e entrega cada cliente a uma thread própria (Atendente)
 * - Thread "udp-recv"  : recebe datagramas UDP (PING) e responde (PONG)
 * - Janela Swing com log em tempo real (se não houver ambiente gráfico, loga no console)
 *
 * Uso: java Servidor [porta]     (padrão: 5000, mesma porta para TCP e UDP)
 *
 * Protocolo (texto, UTF-8, uma linha por mensagem no TCP):
 *   TCP:  MSG|ALGORITMO|CHAVE|TEXTO_CIFRADO
 *   UDP:  PING|seq|timestamp   ->   PONG|seq|timestamp
 */
public class Servidor {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final int porta;
    private final List<Atendente> clientes = new CopyOnWriteArrayList<>();
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private volatile boolean mostrarPings = true;

    // Componentes Swing (só usados na EDT)
    private JTextArea areaLogTcp;
    private JTextArea areaLogUdp;
    private JTextArea areaLogTodos;
    private JTextField campoMsg;

    public Servidor(int porta) {
        this.porta = porta;
    }

    public static void main(String[] args) {
        int porta = args.length > 0 ? Integer.parseInt(args[0]) : 5000;
        Servidor s = new Servidor(porta);
        if (!GraphicsEnvironment.isHeadless()) {
            SwingUtilities.invokeLater(s::criarJanela);
        }
        s.iniciar();
    }

    private void iniciar() {
        log("[INFO]", "Servidor iniciado. Confirme o par (IPSERV, PORT) para o redirecionamento nos roteadores:");
        listarIps();
        new Thread(this::loopTcp, "tcp-accept").start();
        new Thread(this::loopUdp, "udp-recv").start();
    }

    private void listarIps() {
        try {
            Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
            while (ifs.hasMoreElements()) {
                NetworkInterface ni = ifs.nextElement();
                if (!ni.isUp() || ni.isLoopback()) {
                    continue;
                }
                Enumeration<InetAddress> ends = ni.getInetAddresses();
                while (ends.hasMoreElements()) {
                    InetAddress a = ends.nextElement();
                    if (a instanceof Inet4Address) {
                        log("[INFO]", "  IPSERV = " + a.getHostAddress() + "   PORT = " + porta
                                + "   (interface " + ni.getDisplayName() + ")");
                    }
                }
            }
        } catch (SocketException e) {
            log("[ERRO]", "Não consegui listar os IPs: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ TCP

    private void loopTcp() {
        try (ServerSocket ss = new ServerSocket()) {
            ss.setReuseAddress(true);
            ss.bind(new InetSocketAddress(porta));
            log("[TCP]", "Escutando na porta " + porta + " (accept() bloqueia até um cliente conectar)");
            while (true) {
                Socket s = ss.accept();
                Atendente a = new Atendente(s);
                clientes.add(a);
                log("[TCP]", "Nova conexão de " + a.id + " -> thread dedicada criada (clientes ativos: "
                        + clientes.size() + ")");
                pool.execute(a);
            }
        } catch (IOException e) {
            log("[ERRO]", "TCP: " + e.getMessage());
        }
    }

    /** Uma instância por cliente conectado; roda em sua própria thread. */
    private class Atendente implements Runnable {
        private final Socket socket;
        private final PrintWriter out;
        final String id;
        private volatile String alg = Cifra.CESAR;
        private volatile String chave = "3";

        Atendente(Socket socket) throws IOException {
            this.socket = socket;
            this.id = String.valueOf(socket.getRemoteSocketAddress());
            this.out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        }

        @Override
        public void run() {
            log("[THREAD]", Thread.currentThread().getName() + " está atendendo " + id);
            try (Socket s = socket;
                 BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8))) {
                String linha;
                while ((linha = in.readLine()) != null) {
                    tratar(linha);
                }
                log("[TCP]", id + " fechou a conexão");
            } catch (IOException e) {
                log("[TCP]", "Conexão com " + id + " caiu: " + e.getMessage());
            } finally {
                clientes.remove(this);
                log("[TCP]", id + " removido. Clientes ativos: " + clientes.size());
            }
        }

        private void tratar(String linha) {
            String[] p = linha.split("\\|", 4);
            if (p.length == 4 && p[0].equals("MSG")) {
                alg = p[1];
                chave = p[2];
                int bytes = linha.getBytes(StandardCharsets.UTF_8).length;
                log("[TCP]", "Recebidos " + bytes + " bytes de " + id + ": " + linha);
                try {
                    StringBuilder passos = new StringBuilder();
                    String claro = Cifra.processar(alg, chave, p[3], false, passos);
                    log("[CIFRA]", "Decifrando com " + alg + " (chave=" + chave + "): " + passos);
                    log("[CIFRA]", "Texto decifrado: " + claro);
                    log("[CHAT]", id + " disse: " + claro);
                    enviarCifrado("Servidor recebeu: " + claro);
                } catch (RuntimeException e) {
                    log("[ERRO]", "Falha ao decifrar: " + e.getMessage());
                    out.println("ERR|" + e.getMessage());
                }
            } else {
                log("[TCP]", "Linha fora do protocolo: " + linha);
                out.println("ERR|protocolo invalido");
            }
        }

        void enviarCifrado(String texto) {
            texto = texto.replace('\n', ' ').replace('\r', ' ');
            StringBuilder passos = new StringBuilder();
            String cifrado = Cifra.processar(alg, chave, texto, true, passos);
            log("[CIFRA]", "Cifrando resposta com " + alg + " (chave=" + chave + "): " + passos);
            String pacote = "MSG|" + alg + "|" + chave + "|" + cifrado;
            out.println(pacote);
            log("[TCP]", "Enviado para " + id + ": " + pacote);
        }
    }

    private void enviarParaTodos(String texto) {
        if (clientes.isEmpty()) {
            log("[TCP]", "Nenhum cliente conectado para receber a mensagem.");
            return;
        }
        log("[CHAT]", "Servidor disse: " + texto);
        for (Atendente a : clientes) {
            a.enviarCifrado(texto);
        }
    }

    // ------------------------------------------------------------------ UDP

    private void loopUdp() {
        try (DatagramSocket ds = new DatagramSocket(null)) {
            ds.setReuseAddress(true);
            ds.bind(new InetSocketAddress(porta));
            log("[UDP]", "Escutando na porta " + porta + " (sem conexão: cada datagrama é independente)");
            byte[] buf = new byte[1024];
            while (true) {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                ds.receive(p);
                String msg = new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8);
                if (msg.startsWith("PING|")) {
                    if (mostrarPings) {
                        log("[UDP]", "Datagrama de " + p.getSocketAddress() + ": " + msg);
                    }
                    byte[] resp = ("PONG|" + msg.substring(5)).getBytes(StandardCharsets.UTF_8);
                    ds.send(new DatagramPacket(resp, resp.length, p.getAddress(), p.getPort()));
                    if (mostrarPings) {
                        log("[UDP]", "PONG devolvido para " + p.getSocketAddress());
                    }
                } else {
                    log("[UDP]", "Datagrama desconhecido de " + p.getSocketAddress() + ": " + msg);
                }
            }
        } catch (IOException e) {
            log("[ERRO]", "UDP: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ Interface / log

    private void log(String tag, String msg) {
        String linha = "[" + LocalTime.now().format(FMT) + "] " + tag + " " + msg;
        System.out.println(linha);
        SwingUtilities.invokeLater(() -> {
            if (areaLogTodos != null) {
                areaLogTodos.append(linha + "\n");
                areaLogTodos.setCaretPosition(areaLogTodos.getDocument().getLength());
            }
            if (areaLogUdp != null && ("[UDP]".equals(tag) || msg.startsWith("UDP"))) {
                areaLogUdp.append(linha + "\n");
                areaLogUdp.setCaretPosition(areaLogUdp.getDocument().getLength());
            }
            if (areaLogTcp != null && !"[UDP]".equals(tag)) {
                areaLogTcp.append(linha + "\n");
                areaLogTcp.setCaretPosition(areaLogTcp.getDocument().getLength());
            }
        });
    }

    private JTextArea criarAreaLog() {
        JTextArea a = new JTextArea();
        a.setEditable(false);
        a.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        return a;
    }

    private void criarJanela() {
        JFrame f = new JFrame("Servidor (PC2) - porta " + porta + " (TCP e UDP)");
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        areaLogTcp = criarAreaLog();
        areaLogUdp = criarAreaLog();
        areaLogTodos = criarAreaLog();

        JTabbedPane abasLog = new JTabbedPane();
        abasLog.addTab("TCP / Clientes", new JScrollPane(areaLogTcp));
        abasLog.addTab("UDP", new JScrollPane(areaLogUdp));
        abasLog.addTab("Todos", new JScrollPane(areaLogTodos));
        abasLog.setBorder(BorderFactory.createTitledBorder("Log em tempo real do servidor"));

        campoMsg = new JTextField();
        JButton enviar = new JButton("Enviar aos clientes");
        Runnable acao = () -> {
            String t = campoMsg.getText().trim();
            if (!t.isEmpty()) {
                enviarParaTodos(t);
                campoMsg.setText("");
            }
        };
        enviar.addActionListener(e -> acao.run());
        campoMsg.addActionListener(e -> acao.run());

        JCheckBox chkPings = new JCheckBox("Mostrar pings UDP no log", true);
        chkPings.addActionListener(e -> mostrarPings = chkPings.isSelected());

        JPanel baixo = new JPanel(new BorderLayout(6, 6));
        baixo.add(campoMsg, BorderLayout.CENTER);
        baixo.add(enviar, BorderLayout.EAST);
        baixo.add(chkPings, BorderLayout.SOUTH);
        baixo.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        f.add(abasLog, BorderLayout.CENTER);
        f.add(baixo, BorderLayout.SOUTH);
        f.setSize(950, 550);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
    }
}
