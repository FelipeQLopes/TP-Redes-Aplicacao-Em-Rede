import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * CLIENTE (roda no PC1 - Mac) - interface Java Swing
 *
 * Conexão de Rede:
 *  - Na topologia física: conecta-se ao IP da WAN do R3 (TP-Link: 192.168.1.200) na porta 5000.
 *    O R3 faz o Port Forwarding (NAT) para o servidor no PC2 (Windows: 10.0.0.10:5000).
 *  - Em teste local: conecta-se a 127.0.0.1:5000.
 *
 * Threads:
 *  - EDT (Swing)      : interface e envio de mensagens
 *  - "tcp-leitor"     : conecta e fica lendo as respostas do servidor (readLine bloqueia)
 *  - "udp-ping"       : a cada 2 s envia um PING por UDP e mede o RTT
 *
 * Fluxo de uma mensagem:
 *   digita -> cifra (César/Vigenère) -> TCP até o servidor -> servidor decifra e responde cifrado
 *   -> cliente recebe, decifra e mostra na conversa.
 *
 * O painel da direita mostra, em tempo real, cada etapa (cifra, TCP, UDP, erros).
 */
public class Cliente extends JFrame {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final JTextField campoIp = new JTextField("192.168.1.200", 12);
    private final JTextField campoPorta = new JTextField("5000", 5);
    private final JComboBox<String> comboAlg = new JComboBox<>(Cifra.ALGORITMOS);
    private final JTextField campoChave = new JTextField("3", 8);
    private final JButton btnConectar = new JButton("Conectar");

    private final JTextArea areaChat = new JTextArea();
    private final JTextArea areaLogTcp = new JTextArea();
    private final JTextArea areaLogUdp = new JTextArea();
    private final JTextArea areaLogTodos = new JTextArea();
    private final JTextField campoMsg = new JTextField();
    private final JButton btnEnviar = new JButton("Enviar");
    private final JLabel lblStatus = new JLabel("Desconectado");
    private final JLabel lblUdp = new JLabel("UDP: -");

    private Socket socket;
    private PrintWriter out;
    private DatagramSocket udp;
    private ScheduledExecutorService agendador;
    private volatile boolean conectado = false;

    // contadores do ping (usados só pela thread udp-ping)
    private int seq = 0;
    private int enviados = 0;
    private int perdidos = 0;

    public Cliente() {
        super("Chat Cifrado - Cliente (PC1)");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        // ---- topo: configuração
        JPanel topo = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        topo.add(new JLabel("IP WAN R3 (ou Servidor):"));
        topo.add(campoIp);
        topo.add(new JLabel("Porta:"));
        topo.add(campoPorta);
        topo.add(new JLabel("Cifra:"));
        topo.add(comboAlg);
        topo.add(new JLabel("Chave:"));
        topo.add(campoChave);
        topo.add(btnConectar);
        comboAlg.addActionListener(e -> sugerirChave());

        // ---- centro: conversa | log
        areaChat.setEditable(false);
        areaChat.setLineWrap(true);
        areaChat.setWrapStyleWord(true);
        JScrollPane scrollChat = new JScrollPane(areaChat);
        scrollChat.setBorder(BorderFactory.createTitledBorder("Conversa (texto já decifrado)"));

        configurarAreaLog(areaLogTcp);
        configurarAreaLog(areaLogUdp);
        configurarAreaLog(areaLogTodos);

        JTabbedPane abasLog = new JTabbedPane();
        abasLog.addTab("TCP / Cifra", new JScrollPane(areaLogTcp));
        abasLog.addTab("UDP (Ping)", new JScrollPane(areaLogUdp));
        abasLog.addTab("Todos", new JScrollPane(areaLogTodos));
        abasLog.setBorder(BorderFactory.createTitledBorder("O que o código está fazendo (tempo real)"));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scrollChat, abasLog);
        split.setResizeWeight(0.35);

        // ---- baixo: mensagem + status
        JPanel entrada = new JPanel(new BorderLayout(6, 6));
        entrada.add(campoMsg, BorderLayout.CENTER);
        entrada.add(btnEnviar, BorderLayout.EAST);
        JPanel status = new JPanel(new BorderLayout());
        status.add(lblStatus, BorderLayout.WEST);
        status.add(lblUdp, BorderLayout.EAST);
        JPanel baixo = new JPanel(new BorderLayout(6, 6));
        baixo.add(entrada, BorderLayout.CENTER);
        baixo.add(status, BorderLayout.SOUTH);
        baixo.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        add(topo, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
        add(baixo, BorderLayout.SOUTH);

        btnEnviar.setEnabled(false);
        btnConectar.addActionListener(e -> {
            if (conectado) {
                desconectar();
            } else {
                conectar();
            }
        });
        btnEnviar.addActionListener(e -> enviar());
        campoMsg.addActionListener(e -> enviar());

        setSize(1150, 620);
        setLocationRelativeTo(null);
    }

    private void sugerirChave() {
        String alg = (String) comboAlg.getSelectedItem();
        if (Cifra.CESAR.equals(alg)) {
            campoChave.setText("3");
        } else if (Cifra.VIGENERE.equals(alg)) {
            campoChave.setText("redes");
        }
    }

    // ------------------------------------------------------------------ conexão

    private void conectar() {
        final String ip = campoIp.getText().trim();
        final int porta;
        try {
            porta = Integer.parseInt(campoPorta.getText().trim());
        } catch (NumberFormatException e) {
            aviso("Porta inválida.");
            return;
        }
        btnConectar.setEnabled(false);
        lblStatus.setText("Conectando...");

        new Thread(() -> {
            try {
                log("[TCP]", "Abrindo conexão com " + ip + ":" + porta + " ...");
                Socket s = new Socket();
                s.connect(new InetSocketAddress(ip, porta), 3000);
                log("[TCP]", "Conectado! Local=" + s.getLocalSocketAddress() + "  Remoto="
                        + s.getRemoteSocketAddress() + "  (handshake de 3 vias concluído)");
                socket = s;
                out = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true);
                udp = new DatagramSocket();
                udp.setSoTimeout(1000);
                conectado = true;

                SwingUtilities.invokeLater(() -> {
                    btnConectar.setText("Desconectar");
                    btnConectar.setEnabled(true);
                    btnEnviar.setEnabled(true);
                    lblStatus.setText("Conectado a " + ip + ":" + porta);
                });

                seq = 0;
                enviados = 0;
                perdidos = 0;
                agendador = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "udp-ping"));
                agendador.scheduleAtFixedRate(() -> ping(ip, porta), 0, 2, TimeUnit.SECONDS);

                lerMensagens(s);
            } catch (IOException e) {
                log("[ERRO]", "Não foi possível conectar: " + e.getMessage());
                SwingUtilities.invokeLater(() -> {
                    btnConectar.setEnabled(true);
                    lblStatus.setText("Desconectado");
                });
            }
        }, "tcp-leitor").start();
    }

    private synchronized void desconectar() {
        if (!conectado) {
            return;
        }
        conectado = false;
        if (agendador != null) {
            agendador.shutdownNow();
        }
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
        if (udp != null) {
            udp.close();
        }
        log("[TCP]", "Conexão encerrada.");
        SwingUtilities.invokeLater(() -> {
            btnConectar.setText("Conectar");
            btnConectar.setEnabled(true);
            btnEnviar.setEnabled(false);
            lblStatus.setText("Desconectado");
        });
    }

    // ------------------------------------------------------------------ TCP: receber

    private void lerMensagens(Socket s) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8))) {
            String linha;
            while ((linha = in.readLine()) != null) {
                tratarRecebida(linha);
            }
            if (conectado) {
                log("[TCP]", "O servidor encerrou a conexão.");
            }
        } catch (IOException e) {
            if (conectado) {
                log("[ERRO]", "Leitura TCP: " + e.getMessage());
            }
        } finally {
            desconectar();
        }
    }

    private void tratarRecebida(String linha) {
        log("[TCP]", "Recebido do servidor: " + linha);
        String[] p = linha.split("\\|", 4);
        if (p.length == 4 && p[0].equals("MSG")) {
            try {
                StringBuilder passos = new StringBuilder();
                String claro = Cifra.processar(p[1], p[2], p[3], false, passos);
                log("[CIFRA]", "Decifrando com " + p[1] + " (chave=" + p[2] + "): " + passos);
                log("[CIFRA]", "Texto decifrado: " + claro);
                addChat("Servidor: " + claro);
            } catch (RuntimeException e) {
                log("[ERRO]", "Falha ao decifrar: " + e.getMessage());
            }
        } else if (p[0].equals("ERR")) {
            log("[ERRO]", "O servidor respondeu com erro: " + linha);
        }
    }

    // ------------------------------------------------------------------ TCP: enviar

    private void enviar() {
        String texto = campoMsg.getText().trim().replace('\n', ' ');
        if (texto.isEmpty() || !conectado) {
            return;
        }
        String alg = (String) comboAlg.getSelectedItem();
        String chave = campoChave.getText().trim();
        if (chave.contains("|")) {
            aviso("A chave não pode conter o caractere '|'.");
            return;
        }

        StringBuilder passos = new StringBuilder();
        String cifrado;
        try {
            cifrado = Cifra.processar(alg, chave, texto, true, passos);
        } catch (IllegalArgumentException e) {
            aviso("Chave inválida: " + e.getMessage());
            return;
        }

        log("[CIFRA]", "Texto original: " + texto);
        log("[CIFRA]", "Algoritmo=" + alg + " chave=" + chave + " | letra a letra: " + passos);
        log("[CIFRA]", "Texto cifrado: " + cifrado);

        String pacote = "MSG|" + alg + "|" + chave + "|" + cifrado;
        int bytes = pacote.getBytes(StandardCharsets.UTF_8).length;
        out.println(pacote);
        log("[TCP]", "Enviados " + bytes + " bytes ao servidor: " + pacote);

        addChat("Você: " + texto + (Cifra.NENHUMA.equals(alg) ? "" : "    [na rede: " + cifrado + "]"));
        campoMsg.setText("");
    }

    // ------------------------------------------------------------------ UDP: ping

    private void ping(String ip, int porta) {
        try {
            int n = ++seq;
            enviados++;
            long t0 = System.nanoTime();
            byte[] dados = ("PING|" + n + "|" + t0).getBytes(StandardCharsets.UTF_8);
            udp.send(new DatagramPacket(dados, dados.length, InetAddress.getByName(ip), porta));
            log("[UDP]", "PING #" + n + " enviado (sem handshake, sem garantia de entrega)");

            DatagramPacket resp = new DatagramPacket(new byte[256], 256);
            udp.receive(resp);
            String r = new String(resp.getData(), 0, resp.getLength(), StandardCharsets.UTF_8);
            String[] p = r.split("\\|");
            if (p.length >= 3 && p[0].equals("PONG")) {
                double rtt = (System.nanoTime() - Long.parseLong(p[2])) / 1e6;
                log("[UDP]", String.format("PONG #%s recebido: RTT = %.2f ms", p[1], rtt));
                atualizarUdp(String.format("UDP: RTT %.1f ms | enviados %d | perdidos %d (%.0f%%)",
                        rtt, enviados, perdidos, 100.0 * perdidos / enviados));
            }
        } catch (SocketTimeoutException e) {
            perdidos++;
            log("[UDP]", "PING #" + seq + " sem resposta (timeout de 1 s) -> considerado PERDIDO");
            atualizarUdp(String.format("UDP: sem resposta | enviados %d | perdidos %d (%.0f%%)",
                    enviados, perdidos, 100.0 * perdidos / enviados));
        } catch (Exception e) {
            // não deixa a exceção cancelar os próximos pings agendados
            if (conectado) {
                log("[ERRO]", "UDP: " + e.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------ utilidades de interface

    private void atualizarUdp(String texto) {
        SwingUtilities.invokeLater(() -> lblUdp.setText(texto));
    }

    private void addChat(String linha) {
        SwingUtilities.invokeLater(() -> {
            areaChat.append(linha + "\n");
            areaChat.setCaretPosition(areaChat.getDocument().getLength());
        });
    }

    private void log(String tag, String msg) {
        String linha = "[" + LocalTime.now().format(FMT) + "] " + tag + " " + msg + "\n";
        SwingUtilities.invokeLater(() -> {
            areaLogTodos.append(linha);
            areaLogTodos.setCaretPosition(areaLogTodos.getDocument().getLength());

            if ("[UDP]".equals(tag) || msg.startsWith("UDP")) {
                areaLogUdp.append(linha);
                areaLogUdp.setCaretPosition(areaLogUdp.getDocument().getLength());
            }
            if (!"[UDP]".equals(tag)) {
                areaLogTcp.append(linha);
                areaLogTcp.setCaretPosition(areaLogTcp.getDocument().getLength());
            }
        });
    }

    private void configurarAreaLog(JTextArea a) {
        a.setEditable(false);
        a.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        a.setLineWrap(true);
    }

    private void aviso(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Aviso", JOptionPane.WARNING_MESSAGE);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Cliente().setVisible(true));
    }
}
