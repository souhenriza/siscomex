package com.souhenriza.autenticacao.erro;

import com.souhenriza.autenticacao.dto.ErroRespostaAutenticacao;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

/**
 * Fábrica das exceções de falha de comunicação e de erro devolvido pelo portal. Usada por qualquer chamada ao
 * portal, de autenticação ou de serviço, para que os erros tenham a mesma forma.
 */
public final class FalhasPortal {

    private FalhasPortal() {
    }

    /**
     * Erro devolvido pelo portal com código conhecido. A mensagem inclui o texto enviado pelo portal, quando houver.
     *
     * @param codigo     código mapeado do portal, não nulo
     * @param erro       corpo do erro recebido, ou {@code null}
     * @param tempoEspera tempo sugerido antes de nova tentativa, ou {@code null}
     * @return exceção pronta para ser lançada (tipo não genérico)
     * @throws NullPointerException se {@code codigo} for nulo
     */
    public static ExcecaoSiscomex erroDoPortal(CodigoErroPortal codigo, ErroRespostaAutenticacao erro,
                                               Duration tempoEspera) {
        StringBuilder mensagem = new StringBuilder("[").append(codigo.codigo()).append("] ").append(codigo.descricao());

        if (erro != null && erro.mensagem() != null && !erro.mensagem().isBlank()) {
            mensagem.append(" Mensagem do portal: ").append(erro.mensagem());
        }

        return new ExcecaoSiscomex(mensagem.toString(), erro, codigo, tempoEspera);
    }

    /**
     * Falha de transporte: TLS, tempo esgotado ou conexão. Tempo esgotado e conexão são repetíveis; falha de TLS não.
     *
     * @param causa exceção de E/S original
     * @return exceção com a causa e a indicação de repetição (tipo não genérico)
     */
    public static ExcecaoSiscomex falhaDeComunicacao(IOException causa) {
        if (causadaPor(causa, SSLException.class)) {
            return new ExcecaoSiscomex("Falha TLS ao comunicar com o portal. O certificado do servidor pode estar fora do TrustStore ou o certificado do cliente pode não ser válido.", causa);
        }

        if (causa instanceof HttpTimeoutException) {
            return new ExcecaoSiscomex("Tempo esgotado ao comunicar com o portal.", causa, true);
        }

        return new ExcecaoSiscomex("Falha na comunicação com o portal.", causa, true);
    }

    /**
     * Indica se a exceção ou alguma de suas causas é do tipo informado.
     *
     * @param excecao exceção a examinar
     * @param tipo    tipo procurado na cadeia de causas
     * @return {@code true} se encontrado (tipo não genérico)
     */
    private static boolean causadaPor(Throwable excecao, Class<? extends Throwable> tipo) {
        for (Throwable atual = excecao; atual != null; atual = atual.getCause()) {
            if (tipo.isInstance(atual)) {
                return true;
            }
        }
        return false;
    }
}
