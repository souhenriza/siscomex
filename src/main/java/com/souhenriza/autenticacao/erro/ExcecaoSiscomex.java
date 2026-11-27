package com.souhenriza.autenticacao.erro;

import com.souhenriza.autenticacao.dto.ErroRespostaAutenticacao;

import java.io.Serial;
import java.time.Duration;
import java.util.Optional;

/**
 * Exceção da biblioteca para falhas de autenticação, comunicação e erros devolvidos pelo portal.
 * <p>
 * Além da mensagem, informa o código do portal (quando conhecido), o corpo de erro recebido, o tempo sugerido
 * para nova tentativa e se a operação pode ser repetida.
 */
public class ExcecaoSiscomex extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final ErroRespostaAutenticacao erroResposta;
    private final CodigoErroPortal codigoErroPortal;
    private final Duration tempoEspera;
    private final boolean repetivel;

    /**
     * Exceção completa, com código do portal e tempo de espera.
     *
     * @param mensagem      texto em português descrevendo a falha
     * @param erroResposta  corpo de erro recebido do portal, ou {@code null}
     * @param codigoErroPortal código mapeado do portal, ou {@code null}
     * @param tempoEspera   tempo sugerido antes de nova tentativa, ou {@code null}
     */
    public ExcecaoSiscomex(String mensagem, ErroRespostaAutenticacao erroResposta,
                           CodigoErroPortal codigoErroPortal, Duration tempoEspera) {
        this(mensagem, null, erroResposta, codigoErroPortal, tempoEspera, false);
    }

    /**
     * Exceção com o corpo de erro do portal, sem código mapeado.
     *
     * @param mensagem     texto em português descrevendo a falha
     * @param erroResposta corpo de erro recebido do portal, ou {@code null}
     */
    public ExcecaoSiscomex(String mensagem, ErroRespostaAutenticacao erroResposta) {
        this(mensagem, null, erroResposta, null, null, false);
    }

    /**
     * Exceção apenas com mensagem.
     *
     * @param mensagem texto em português descrevendo a falha
     */
    public ExcecaoSiscomex(String mensagem) {
        this(mensagem, null, null, null, null, false);
    }

    /**
     * Exceção com a causa original.
     *
     * @param mensagem texto em português descrevendo a falha
     * @param causa    exceção que originou a falha
     */
    public ExcecaoSiscomex(String mensagem, Throwable causa) {
        this(mensagem, causa, null, null, null, false);
    }

    /**
     * Exceção com a causa original e a indicação de repetição.
     *
     * @param mensagem  texto em português descrevendo a falha
     * @param causa     exceção que originou a falha
     * @param repetivel {@code true} se a operação pode ser repetida
     */
    public ExcecaoSiscomex(String mensagem, Throwable causa, boolean repetivel) {
        this(mensagem, causa, null, null, null, repetivel);
    }

    /**
     * Construtor principal, usado pelos demais.
     *
     * @param mensagem         texto em português descrevendo a falha
     * @param causa            exceção original, ou {@code null}
     * @param erroResposta     corpo de erro do portal, ou {@code null}
     * @param codigoErroPortal código mapeado do portal, ou {@code null}
     * @param tempoEspera      tempo sugerido antes de nova tentativa, ou {@code null}
     * @param repetivel        indicação explícita de repetição; também é verdadeira quando há tempo de espera
     *                         ou código de erro repetível
     */
    private ExcecaoSiscomex(String mensagem, Throwable causa, ErroRespostaAutenticacao erroResposta,
                            CodigoErroPortal codigoErroPortal, Duration tempoEspera, boolean repetivel) {
        super(mensagem, causa);
        this.erroResposta = erroResposta;
        this.codigoErroPortal = codigoErroPortal;
        this.tempoEspera = tempoEspera;
        this.repetivel = repetivel || tempoEspera != null
                || (codigoErroPortal != null && codigoErroPortal.repetivel());
    }

    /**
     * Corpo de erro recebido do portal.
     *
     * @return erro do portal, ou {@code null} quando a falha não veio de uma resposta com corpo (tipo não genérico)
     */
    public ErroRespostaAutenticacao erroResposta() {
        return erroResposta;
    }

    /**
     * Código mapeado do portal, quando conhecido.
     *
     * @return código, ou vazio (tipo genérico {@code Optional<CodigoErroPortal>})
     */
    public Optional<CodigoErroPortal> codigoErroPortal() {
        return Optional.ofNullable(codigoErroPortal);
    }

    /**
     * Tempo a aguardar antes de tentar novamente, quando o portal ou a janela de parada o informa.
     *
     * @return tempo de espera, ou vazio (tipo genérico {@code Optional<Duration>})
     */
    public Optional<Duration> tempoEspera() {
        return Optional.ofNullable(tempoEspera);
    }

    /**
     * Indica se a operação pode ser repetida.
     *
     * @return {@code true} se pode ser repetida (tipo não genérico)
     */
    public boolean ehRepetivel() {
        return repetivel;
    }
}
