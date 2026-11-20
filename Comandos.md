# Trabalho de Redes

## 1. Configuração do Roteador 1 (R1)

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

end
write memory
```

## 2. Configuração do Roteador 3 (R3)

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

end
write memory
```

## 3. Configuração de NAT no Roteador 1 (R1)

```bash
configure terminal

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

## 4. Configuração de NAT no Roteador 3 (R3)

```bash
configure terminal

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

## 5. Configuração dos PCs

| Configuração | PC1 | PC2 |
|---|---|---|
| IPv4 Address | `192.168.0.10` | `10.0.0.10` |
| Subnet Mask | `255.255.0.0` | `255.0.0.0` |
| Default Gateway | `192.168.0.1` | `10.0.0.1` |

## 6. Testes

**1. PC1 – Testar comunicação com o gateway R1**
```bash
ping 192.168.0.1
```

**2. PC2 – Testar comunicação com o gateway R3**
```bash
ping 10.0.0.1
```

**3. PC1 – Testar comunicação com o PC2**
```bash
ping 10.0.0.10
```

**4. PC2 – Testar comunicação com o PC1**
```bash
ping 192.168.0.10
```
