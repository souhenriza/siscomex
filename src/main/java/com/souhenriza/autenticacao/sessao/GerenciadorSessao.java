package com.souhenriza.autenticacao.sessao;

import com.souhenriza.autenticacao.Autenticador;
import com.souhenriza.autenticacao.erro.ExcecaoSiscomex;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Obtém, renova e reaproveita a sessão usada pelos serviços do portal.
 * <p>
 * A sessão é reaproveitada enquanto estiver dentro da validade (55 minutos, antes do limite de 1 hora do
 * contrato). Duas autenticações nunca ocorrem com intervalo menor que o mínimo (60 segundos). Quando o portal
 * rejeita a sessão, {@link #executar} descarta a sessão e repete a chamada uma vez com uma sessão nova.
 */
public class GerenciadorSessao {

    /** Validade padrão da sessão, antes do limite do contrato. */
    private static final Duration VALIDADE_PADRAO = Duration.ofMinutes(55);

    /** Intervalo mínimo entre autenticações válidas. */
    private static final Duration INTERVALO_MINIMO_PADRAO = Duration.ofSeconds(60);

    private final Autenticador autenticador;
    private final Clock relogio;
    private final Duration validade;
    private final Pausa pausa;
    private final Duration intervaloMinimo;

    private final ReentrantLock trava = new ReentrantLock();
    private final AtomicReference<Sessao> sessaoAtual = new AtomicReference<>();

    private Instant ultimaTentativa;

    /**
     * Gerenciador com os valores padrão: validade de 55 minutos, intervalo mínimo de 60 segundos e relógio do sistema.
     *
     * @param autenticador quem obtém os tokens no portal
     * @throws NullPointerException if {@code autenticador} for nulo
     */
    public GerenciadorSessao(Autenticador autenticador) {
        this(autenticador, Clock.systemUTC(), VALIDADE_PADRAO, INTERVALO_MINIMO_PADRAO, Pausa.bloqueante());
    }

    /**
     * Gerenciador completo, com todos os parâmetros injetáveis (útil nos testes).
     *
     * @param autenticador    quem obtém os tokens no portal
     * @param relogio         relógio usado para validade e intervalo
     * @param validade        validade da sessão, positiva
     * @param intervaloMinimo intervalo mínimo entre autenticações, não negativo
     * @param pausa           espera usada para respeitar o intervalo mínimo
     * @throws NullPointerException     if algum argumento for nulo
     * @throws IllegalArgumentException se a validade não for positiva ou o intervalo for negativo
     */
    public GerenciadorSessao(Autenticador autenticador, Clock relogio, Duration validade, Duration intervaloMinimo,
                             Pausa pausa) {
        this.autenticador = Objects.requireNonNull(autenticador, "autenticador");
        this.relogio = Objects.requireNonNull(relogio, "relogio");
        this.validade = Objects.requireNonNull(validade, "validade");
        this.pausa = Objects.requireNonNull(pausa);
        this.intervaloMinimo = Objects.requireNonNull(intervaloMinimo, "intervaloMinimo");
        if (validade.isNegative() || validade.isZero()) {
            throw new IllegalArgumentException("validade deve ser positiva");
        }

        if (intervaloMinimo.isNegative()) {
            throw new IllegalArgumentException("intervaloMinimo não pode ser negativo");
        }
    }

    /**
     * Devolve a sessão vigente. Se ela estiver vencida ou ausente, autentica de novo (respeitando o intervalo
     * mínimo). Duas threads que chamem ao mesmo tempo não disparam duas autenticações.
     *
     * @return sessão válida (tipo não genérico)
     * @throws ExcecaoSiscomex se a autenticação falhar ou se a espera pelo intervalo mínimo for interrompida
     */
    public Sessao obterSessaoValida() {
        Sessao atual = sessaoAtual.get();

        if (ehValida(atual)) {
            return atual;
        }

        trava.lock();

        try {
            atual = sessaoAtual.get();
            if (ehValida(atual)) {
                return atual;
            }
            return autenticar();
        } finally {
            trava.unlock();
        }
    }

    /**
     * Atualiza a sessão com tokens que o portal devolveu em uma resposta. Um valor nulo ou vazio mantém o token
     * anterior. Se o JWT for trocado, a data de obtenção também é atualizada. Sem sessão ativa, não faz nada.
     *
     * @param novoTokenAutenticacao JWT novo, ou {@code null} para manter o atual
     * @param novoTokenCsrf         CSRF novo, ou {@code null} para manter o atual
     */
    public void atualizarAPartirDaResposta(String novoTokenAutenticacao, String novoTokenCsrf) {
        boolean temAutenticacao = novoTokenAutenticacao != null && !novoTokenAutenticacao.isBlank();
        boolean temCsrf = novoTokenCsrf != null && !novoTokenCsrf.isBlank();

        if (!temAutenticacao && !temCsrf) {
            return;
        }

        Instant agora = relogio.instant();

        sessaoAtual.updateAndGet(s -> {
            if (s == null) {
                return null;
            }

            return new Sessao(temAutenticacao ? novoTokenAutenticacao : s.tokenAutenticacao(),
                    temCsrf ? novoTokenCsrf : s.tokenCsrf(),
                    temAutenticacao ? agora : s.obtidaEm());
        });
    }

    /**
     * Descarta a sessão informada, se ela ainda for a vigente. Se outra thread já tiver renovado a sessão, a nova
     * é mantida.
     *
     * @param sessaoUsada sessão que foi rejeitada, não nula
     * @throws NullPointerException se {@code sessaoUsada} for nula
     */
    public void invalidar(Sessao sessaoUsada) {
        Objects.requireNonNull(sessaoUsada, "sessaoUsada");
        sessaoAtual.updateAndGet(s -> (s != null && s.tokenAutenticacao().equals(sessaoUsada.tokenAutenticacao())) ? null : s);
    }

    /**
     * Executa uma chamada com a sessão vigente. Se o resultado indicar sessão rejeitada, descarta a sessão e repete
     * a chamada uma única vez com uma sessão nova.
     *
     * @param <R>            tipo do resultado da chamada
     * @param chamada        operação que recebe a sessão e devolve o resultado
     * @param sessaoRejeitada predicado que indica se o resultado significa sessão rejeitada
     * @return resultado da chamada, da primeira tentativa ou da repetição (tipo genérico {@code R})
     * @throws NullPointerException if algum argumento for nulo
     * @throws ExcecaoSiscomex      se a autenticação falhar ou se a chamada lançar essa exceção
     */
    public <R> R executar(Function<Sessao, R> chamada, Predicate<R> sessaoRejeitada) {
        Objects.requireNonNull(chamada, "chamada");
        Objects.requireNonNull(sessaoRejeitada, "sessaoRejeitada");

        Sessao usada = obterSessaoValida();
        R resposta = chamada.apply(usada);
        if (!sessaoRejeitada.test(resposta)) {
            return resposta;
        }

        invalidar(usada);
        return chamada.apply(obterSessaoValida());
    }

    /**
     * Autentica no portal, respeitando o intervalo mínimo, e guarda a nova sessão.
     *
     * @return sessão recém-obtida (tipo não genérico)
     * @throws ExcecaoSiscomex se a espera for interrompida, se o autenticador não devolver tokens, ou se a
     *                         autenticação falhar
     */
    private Sessao autenticar() {
        aguardarIntervaloMinimo();
        ultimaTentativa = relogio.instant();
        Tokens tokens = autenticador.autenticar();

        if (tokens == null) {
            throw new ExcecaoSiscomex("O autenticador não retornou tokens.");
        }
        Sessao novaSessao = new Sessao(tokens.tokenAutenticacao(), tokens.tokenCsrf(), relogio.instant());
        sessaoAtual.set(novaSessao);
        return novaSessao;
    }

    /**
     * Espera enquanto o intervalo desde a última autenticação for menor que o mínimo.
     *
     * @throws ExcecaoSiscomex se a thread for interrompida durante a espera; o estado de interrupção é restaurado
     */
    private void aguardarIntervaloMinimo() {
        Duration falta = tempoPendente();
        while (!falta.isNegative() && !falta.isZero()) {
            try {
                pausa.dormir(falta);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ExcecaoSiscomex("Interrompido enquanto aguardava o intervalo mínimo entre as autenticações", e);
            }
            falta = tempoPendente();
        }
    }

    /**
     * Quanto falta para o intervalo mínimo se cumprir desde a última tentativa.
     *
     * @return duração restante; zero se nunca houve tentativa (tipo não genérico)
     */
    private Duration tempoPendente() {
        if (ultimaTentativa == null) {
            return Duration.ZERO;
        }

        Duration decorrido = Duration.between(ultimaTentativa, relogio.instant());

        return intervaloMinimo.minus(decorrido);
    }

    /**
     * Indica se a sessão existe e ainda está dentro da validade.
     *
     * @param sessao sessão a verificar, pode ser nula
     * @return {@code true} se houver sessão e ela estiver dentro da validade (tipo não genérico)
     */
    private boolean ehValida(Sessao sessao) {
        return sessao != null && relogio.instant().isBefore(sessao.obtidaEm().plus(validade));
    }
}
