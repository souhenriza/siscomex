package com.souhenriza.auth;

import com.souhenriza.error.SiscomexException;
import com.souhenriza.session.Tokens;

public interface Authenticator {

    Tokens authenticate() throws SiscomexException;
}
