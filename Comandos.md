# Roteiro de Configuração de Rede e Roteadores

Este documento detalha a configuração da topologia física de laboratório utilizando os roteadores **Nokia G-1425G-A** (R1) e **TP-Link TL-WR642G** (R3), além de manter o histórico de comandos para simulação no Cisco Packet Tracer (`ProjetoRede.pkt`).

---

## 🌐 1. Topologia Física (2 Roteadores — Sem R2)

```text
[ PC1 - Mac (Cliente) ]
       │ IP: 192.168.1.10 (ou DHCP) | Máscara: 255.255.0.0 (ou 255.255.255.0)
       ▼ Gateway: 192.168.1.254
┌──────────────────────────────────────────────────────────────┐
│ Roteador 1 (R1) — Nokia G-1425G-A                            │
│ - Interface LAN: 192.168.1.254 (Sub-rede 192.168.0.0/16 ou /24)
│ - Port Forwarding: Porta 5000 (TCP/UDP) -> 192.168.1.200     │
└──────────────────────────────┬───────────────────────────────┘
                               │ Cabo RJ45 direto: LAN 1 do R1 ──► WAN do R3
                               ▼
┌──────────────────────────────────────────────────────────────┐
│ Roteador 3 (R3) — TP-Link TL-WR642G                          │
│ - Interface WAN: 192.168.1.200 (Estático) | Gateway: 192.168.1.254
│ - Interface LAN: 10.0.0.1 (Sub-rede 10.0.0.0/8 ou /24)       │
│ - Forwarding / Virtual Servers:                              │
│     Porta 5000 (TCP e UDP) -> 10.0.0.10:5000 (PC2)          │
└──────────────────────────────┬───────────────────────────────┘
                               │ Cabo RJ45: Porta LAN do R3
                               ▼
[ PC2 - Windows (Servidor) ]
       IP: 10.0.0.10 | Máscara: 255.0.0.0 (ou 255.255.255.0)
       Gateway: 10.0.0.1 | Executa a aplicação Java na porta 5000 (TCP e UDP)
```

> **Por que o Cliente (PC1 / Mac) conecta em `192.168.1.200:5000`?**  
> O PC1 está conectado à rede LAN do R1. A porta WAN do R3 também está conectada à LAN do R1 com o IP `192.168.1.200`. Ambos estão no mesmo domínio de rede local. Ao conectar em `192.168.1.200:5000`, a requisição atinge a WAN do R3, que pelo *Virtual Server* (Port Forwarding) repassa os pacotes para o IP privado do PC2 (`10.0.0.10:5000`).

---

## 🛠️ 2. Configuração do Roteador 1 (R1 — Nokia G-1425G-A)

Acesse a interface de gerenciamento web do Nokia (geralmente em `http://192.168.1.254`):

1. **Configuração da LAN**:
   - **IP Address**: `192.168.1.254`
   - **Subnet Mask**: `255.255.0.0` (ou `255.255.255.0`, conforme aceito pelo firmware)
   - **DHCP Server**: Habilitado na faixa (ex.: `192.168.1.1` a `192.168.1.199`) para que o PC1 receba IP automaticamente ou use estático.

2. **Port Forwarding (Redirecionamento de Portas)**:
   - Navegue até **Network / Application / Port Forwarding** (ou **NAT / Virtual Server**):
   - **Service Name**: `JavaChat`
   - **WAN / External Port**: `5000`
   - **Internal / LAN IP**: `192.168.1.200` (IP estático da WAN do R3)
   - **Internal Port**: `5000`
   - **Protocol**: `TCP/UDP` (ou crie duas regras: uma TCP e outra UDP)
   - **Status**: Ativado (*Enabled*)

---

## 🛠️ 3. Configuração do Roteador 3 (R3 — TP-Link TL-WR642G)

Acesse a interface web do TP-Link (conectado provisoriamente em uma porta LAN em `http://10.0.0.1` ou `192.168.1.1` original antes de alterar):

1. **Configuração da WAN** (**Network -> WAN**):
   - **WAN Connection Type**: `Static IP`
   - **IP Address**: `192.168.1.200`
   - **Subnet Mask**: `255.255.255.0` (ou `255.255.0.0`)
   - **Default Gateway**: `192.168.1.254` (IP LAN do Nokia R1)
   - **Primary DNS**: `192.168.1.254` ou `8.8.8.8`
   - Clique em **Save**.

2. **Configuração da LAN** (**Network -> LAN**):
   - **IP Address**: `10.0.0.1`
   - **Subnet Mask**: `255.0.0.0` (ou `255.255.255.0`)
   - Clique em **Save** e confirme a reinicialização caso solicitada.

3. **Virtual Servers (Port Forwarding)** (**Forwarding -> Virtual Servers**):
   - Clique em **Add New...**:
     - **Service Port**: `5000`
     - **IP Address**: `10.0.0.10` (IP estático do PC2)
     - **Protocol**: `ALL` (ou crie duas entradas: uma `TCP` e outra `UDP`)
     - **Status**: `Enabled`
   - Clique em **Save**.

---

## 💻 4. Configuração das Estações (PC1 e PC2)

### PC1 (Cliente — Mac)
- Conectado via cabo ou Wi-Fi à rede LAN do R1 (Nokia).
- **Endereço IPv4**: `192.168.1.10` (ou atribuído via DHCP na faixa `192.168.1.x`)
- **Máscara de Sub-rede**: `255.255.0.0` ou `255.255.255.0`
- **Roteador / Gateway Padrão**: `192.168.1.254`

### PC2 (Servidor — Windows)
- Conectado via cabo a uma das portas LAN (amarelas) do R3 (TP-Link).
- **Endereço IPv4**: `10.0.0.10` (Estático)
- **Máscara de Sub-rede**: `255.0.0.0` ou `255.255.255.0`
- **Gateway Padrão**: `10.0.0.1`
- **Firewall do Windows**:
  - Liberar a porta `5000` nas regras de entrada para **TCP** e **UDP**, ou desativar temporariamente o Firewall no perfil de Rede Privada para testes.

---

## 🧪 5. Roteiro de Testes e Diagnóstico

### Teste 1: Conectividade Básica (Ping)
No terminal do Mac (PC1):
```bash
# 1. Ping no Gateway Nokia (R1)
ping 192.168.1.254

# 2. Ping na interface WAN do TP-Link (R3)
ping 192.168.1.200
```
*(Se o R3 não responder ping na WAN, verifique se a opção "Ignore Ping Packet from WAN Port" / ICMP Echo está desativada nas configurações de segurança do TP-Link; o tráfego TCP/UDP na porta 5000 funcionará normalmente mesmo se o ping na WAN for descartado).*

No Prompt de Comando do Windows (PC2):
```cmd
# Ping no Gateway TP-Link (R3)
ping 10.0.0.1
```

### Teste 2: Execução da Aplicação

1. **No PC2 (Windows — Servidor)**:
   ```cmd
   javac -encoding UTF-8 app\*.java
   java -cp app Servidor 5000
   ```
   *O console exibirá `IPSERV = 10.0.0.10 PORT = 5000` e aguardará conexões.*

2. **No PC1 (Mac — Cliente)**:
   ```bash
   javac -encoding UTF-8 app/*.java
   java -cp app Cliente
   ```
   *Na interface gráfica:*
   - **IP WAN R3 (ou Servidor)**: `192.168.1.200`
   - **Porta**: `5000`
   - Clique em **Conectar**.
   - Observe os logs em tempo real das abas `TCP / Cifra` (handshake e mensagens) e `UDP (Ping)` (medições periódicas de RTT).

---

## 📦 Apêndice: Simulação no Cisco Packet Tracer (`ProjetoRede.pkt`)

Caso deseje simular a topologia no Cisco Packet Tracer com roteadores Cisco IOS:

### R1 (Cisco IOS)
```bash
enable
configure terminal
hostname R1

interface g0/0
 ip address 192.168.0.1 255.255.0.0
 no shutdown

interface g0/1
 ip address 172.16.1.1 255.255.255.0
 no shutdown

exit
ip route 10.0.0.0 255.0.0.0 172.16.1.2

interface g0/0
 ip nat outside

interface g0/1
 ip nat inside
exit

ip nat inside source static tcp 172.16.1.2 5000 192.168.0.1 5000
ip nat inside source static udp 172.16.1.2 5000 192.168.0.1 5000

end
write memory
```

### R3 (Cisco IOS)
```bash
enable
configure terminal
hostname R3

interface g0/0
 ip address 172.16.1.2 255.255.255.0
 no shutdown

interface g0/1
 ip address 10.0.0.1 255.0.0.0
 no shutdown

exit
ip route 192.168.0.0 255.255.0.0 172.16.1.1

interface g0/0
 ip nat outside

interface g0/1
 ip nat inside
exit

ip nat inside source static tcp 10.0.0.10 5000 172.16.1.2 5000
ip nat inside source static udp 10.0.0.10 5000 172.16.1.2 5000

end
write memory
```
