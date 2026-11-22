# Chat Cifrado - Aplicação em Rede (TCP & UDP)

Trabalho Prático da disciplina de **Redes de Computadores I** — PUC Minas.

Aplicação de rede cliente-servidor desenvolvida em Java (com interface gráfica Swing no cliente e no servidor), demonstrando o uso simultâneo de **TCP**, **UDP**, **multithreading**, **cifras didáticas** e **redirecionamento de portas (NAT)** através de roteadores.

---

## 📌 Visão Geral do Projeto

A aplicação consiste em um chat bidirecional seguro/cifrado entre duas máquinas:
- **PC1 (Cliente)**: Interface gráfica Swing com campos para IP/porta, escolha de algoritmo de cifra (César, Vigenère ou Nenhuma), área de chat e painel de logs em tempo real dividido em abas.
- **PC2 (Servidor)**: Processo multithread que atende múltiplos clientes simultaneamente via TCP, responde a datagramas UDP e permite envio de mensagens globais (*broadcast*).

### Por que TCP e UDP na mesma aplicação?
- **TCP (porta 5000)**: Utilizado para a troca de mensagens de texto (chat). Demanda conexão confiável, entrega ordenada e controle de integridade para que nenhuma mensagem ou caractere seja perdido ou corrompido.
- **UDP (porta 5000)**: Utilizado para envio periódico de sondas de latência (`PING`/`PONG`), medição de RTT (*Round Trip Time*) e diagnóstico de perda de pacotes. Demanda baixo overhead e dispensa handshake/confirmação pesada.

---

## 🌐 Topologia de Rede e Roteamento (NAT)

A topologia possui **exatamente 2 roteadores** intermediários (R1 e R2 — rotulado como R3 no arquivo do Packet Tracer `ProjetoRede.pkt` e no `Comandos.md`):

```text
[ PC1 - Cliente ] 
       │ 192.168.0.10/16
       ▼ (Gateway: 192.168.0.1)
┌────────────────┐
│  Roteador 1 R1 │ (g0/0: 192.168.0.1 | g0/1: 172.16.1.1)
└───────┬────────┘
        │ NAT estático: 192.168.0.1:5000 -> 172.16.1.2:5000 (TCP/UDP)
        ▼
┌────────────────┐
│  Roteador 2 R3 │ (g0/0: 172.16.1.2 | g0/1: 10.0.0.1)
└───────┬────────┘
        │ NAT estático: 172.16.1.2:5000 -> 10.0.0.10:5000 (TCP/UDP)
        ▼
[ PC2 - Servidor ] (10.0.0.10/8, porta 5000 TCP e UDP)
```

### Regras da Topologia
1. **Cliente como Iniciador**: O PC1 sempre inicia as conexões TCP e os datagramas UDP em direção a `192.168.0.1:5000`. O servidor nunca abre conexão reversa.
2. **Mesma Porta**: Tanto TCP quanto UDP utilizam a porta padrão `5000`.
3. **Redirecionamento em Cascata**: Os pacotes destinados ao IP externo de R1 são repassados até o servidor no PC2 pelas regras de NAT estático.

---

## 🧵 Arquitetura Multithreading

### Cliente (`Cliente.java`)
- **EDT (Event Dispatch Thread)**: Gerencia a interface gráfica Swing e reage aos cliques e digitação.
- **`tcp-leitor`**: Thread dedicada para efetuar a conexão `Socket.connect()` e bloquear em `BufferedReader.readLine()` aguardando mensagens recebidas sem travar a interface.
- **`udp-ping`**: Thread agendada (`ScheduledExecutorService`) disparada a cada 2 segundos para enviar sondas UDP e calcular RTT com timeout de 1 segundo.

### Servidor (`Servidor.java`)
- **`tcp-accept`**: Loop que bloqueia em `ServerSocket.accept()`. A cada novo cliente conectado, instancia um `Atendente` e despacha para o pool de threads.
- **`pool` (CachedThreadPool)**: Cada cliente conectado é atendido por uma thread independente da classe interna `Atendente`.
- **`udp-recv`**: Thread dedicada que escuta datagramas em `DatagramSocket.receive()` e devolve o eco `PONG` para o endereço e porta de origem.

---

## 🔒 Cifras Didáticas (`Cifra.java`)

Os algoritmos implementados operam sobre caracteres ASCII (A-Z, a-z):
- **CÉSAR**: Cifra de substituição monoalfabética baseada no deslocamento inteiro informado como chave (ex.: `3`).
- **VIGENÈRE**: Cifra polialfabética que utiliza uma palavra-chave para alternar deslocamentos letra a letra (ex.: `"redes"`).
- **NENHUMA**: Texto trafega em claro, ideal para comparação e validação no Wireshark.

> **Nota didática**: A chave viaja no pacote (`MSG|ALGORITMO|CHAVE|TEXTO_CIFRADO`) para fins demonstrativos e de inspeção acadêmica.

---

## 📡 Protocolo da Camada de Aplicação

### TCP (Texto delimitado por quebra de linha `\n`, codificação UTF-8)
- **Mensagem**: `MSG|<ALGORITMO>|<CHAVE>|<TEXTO_CIFRADO>`
  - *Exemplo*: `MSG|CESAR|3|rod` (texto decifrado: `ola`)
- **Erro**: `ERR|<motivo>`

### UDP (Datagramas independentes, UTF-8)
- **Cliente envia**: `PING|<seq>|<timestampNanos>`
- **Servidor responde**: `PONG|<seq>|<timestampNanos>`

---

## 🖥️ Interface Gráfica e Visualização em Tempo Real

Para facilitar a apresentação e a visualização ao vivo:
- **Área de Chat**: Mostra as mensagens trocadas já decifradas.
- **Painel de Log com Abas**:
  - `TCP / Cifra`: Acompanha conexão, envio/recebimento de pacotes TCP e o passo a passo da cifra sem interferência.
  - `UDP (Ping)`: Registra os envios de PING, recepção de PONG, medições de RTT e eventuais timeouts/perdas.
  - `Todos`: Visão cronológica unificada de todos os eventos da aplicação.
- **Barra de Status**: Indica estado da conexão TCP e estatísticas resumidas de UDP (RTT atual, pacotes enviados, perdidos e taxa percentual).

---

## 🚀 Como Compilar e Executar

### Pré-requisitos
- Java Development Kit (JDK 11 ou superior instalado).
- Sem nenhuma biblioteca externa (apenas Java Standard Edition).

### 1. Compilação
Na raiz do projeto:
```bash
javac -encoding UTF-8 app/*.java
```

### 2. Executando o Servidor (PC2 ou teste local)
```bash
java -cp app Servidor 5000
```
*(O servidor exibirá a lista de IPs locais e iniciará a escuta nas portas TCP e UDP).*

### 3. Executando o Cliente (PC1 ou teste local)
```bash
java -cp app Cliente
```

### 4. Cenários de Teste

#### A. Teste Local (mesma máquina)
- No campo **IP do roteador (R1)** do cliente: digite `127.0.0.1`.
- Clique em **Conectar**.
- Envie mensagens alternando os algoritmos de cifra (César, Vigenère, Nenhuma).
- Digite uma mensagem no servidor para testar o envio aos clientes.

#### B. Teste na Topologia com Roteadores (Laboratório / PCs reais)
- Certifique-se de que os PCs e roteadores estão configurados conforme a tabela do [Comandos.md](Comandos.md):
  - PC1: `192.168.0.10` (Gateway `192.168.0.1`)
  - PC2: `10.0.0.10` (Gateway `10.0.0.1`)
- No PC2, inicie o `Servidor`.
- No PC1, inicie o `Cliente`, mantenha o IP `192.168.0.1` e porta `5000` e conecte.

---

## 🦈 Análise no Wireshark

Filtros recomendados para captura e análise dos pacotes durante a demonstração:
- Capturar apenas o tráfego da aplicação:
  ```text
  tcp.port == 5000 || udp.port == 5000
  ```
- Observar o handshake de 3 vias do TCP (SYN, SYN-ACK, ACK):
  ```text
  tcp.port == 5000 && tcp.flags.syn == 1
  ```
- Inspecionar o texto cifrado vs texto em claro no payload TCP:
  - Clicar com o botão direito no pacote TCP -> **Follow** -> **TCP Stream**.
- Observar o tráfego de datagramas UDP sem conexão:
  ```text
  udp.port == 5000
  ```

---

## 📂 Estrutura do Repositório

```text
.
├── app/
│   ├── Cifra.java       # Implementação das cifras (César, Vigenère, Nenhuma)
│   ├── Servidor.java    # Servidor multithread TCP/UDP com interface Swing
│   └── Cliente.java     # Cliente com interface gráfica Swing e abas de log
├── Comandos.md          # Roteiro de configuração dos roteadores (rotas e NAT)
├── ProjetoRede.pkt      # Topologia simulada no Cisco Packet Tracer
└── README.md            # Documentação completa do projeto
```
