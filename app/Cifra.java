/**
 * Cifras didáticas usadas pelo cliente e pelo servidor.
 * ATENÇÃO: César e Vigenère NÃO são seguras. Servem apenas para demonstrar
 * que o conteúdo do pacote viaja "embaralhado" pela rede.
 *
 * Só letras ASCII (A-Z, a-z) são cifradas; acentos, números e símbolos passam direto.
 */
public class Cifra {

    public static final String NENHUMA = "NENHUMA";
    public static final String CESAR = "CESAR";
    public static final String VIGENERE = "VIGENERE";
    public static final String[] ALGORITMOS = {CESAR, VIGENERE, NENHUMA};

    /**
     * Cifra ou decifra o texto.
     *
     * @param alg    CESAR, VIGENERE ou NENHUMA
     * @param chave  CESAR: número inteiro (ex.: 3). VIGENERE: palavra (ex.: "redes")
     * @param texto  texto de entrada
     * @param cifrar true para cifrar, false para decifrar
     * @param passos se não for null, recebe a explicação letra a letra (máx. 12 letras)
     * @throws IllegalArgumentException se o algoritmo ou a chave forem inválidos
     */
    public static String processar(String alg, String chave, String texto, boolean cifrar, StringBuilder passos) {
        if (NENHUMA.equals(alg)) {
            if (passos != null) {
                passos.append("(sem cifra: o texto viaja em claro e aparece legível no Wireshark)");
            }
            return texto;
        }
        int[] deslocamentos = deslocamentos(alg, chave);
        StringBuilder sb = new StringBuilder();
        int indice = 0;
        int mostrados = 0;

        for (char c : texto.toCharArray()) {
            char base;
            if (c >= 'a' && c <= 'z') {
                base = 'a';
            } else if (c >= 'A' && c <= 'Z') {
                base = 'A';
            } else {
                sb.append(c);
                continue;
            }
            int d = deslocamentos[indice % deslocamentos.length];
            indice++;
            if (!cifrar) {
                d = -d;
            }
            char novo = (char) (base + Math.floorMod(c - base + d, 26));
            sb.append(novo);

            if (passos != null && mostrados < 12) {
                passos.append(c).append("->").append(novo)
                      .append(" (").append(d >= 0 ? "+" : "").append(d).append(")  ");
                mostrados++;
            }
        }
        if (passos != null && mostrados == 12) {
            passos.append("...");
        }
        return sb.toString();
    }

    private static int[] deslocamentos(String alg, String chave) {
        if (CESAR.equals(alg)) {
            int k;
            try {
                k = Integer.parseInt(chave.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("A chave do César precisa ser um número inteiro (ex.: 3)");
            }
            return new int[]{Math.floorMod(k, 26)};
        }
        if (VIGENERE.equals(alg)) {
            StringBuilder letras = new StringBuilder();
            for (char c : chave.toLowerCase().toCharArray()) {
                if (c >= 'a' && c <= 'z') {
                    letras.append(c);
                }
            }
            if (letras.length() == 0) {
                throw new IllegalArgumentException("A chave do Vigenère precisa ter ao menos uma letra (a-z)");
            }
            int[] r = new int[letras.length()];
            for (int i = 0; i < r.length; i++) {
                r[i] = letras.charAt(i) - 'a';
            }
            return r;
        }
        throw new IllegalArgumentException("Algoritmo desconhecido: " + alg);
    }
}
