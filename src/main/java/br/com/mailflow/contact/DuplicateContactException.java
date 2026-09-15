package br.com.mailflow.contact;

public class DuplicateContactException extends RuntimeException {

    public DuplicateContactException() {
        super("Já existe um contato com esse endereço de e-mail.");
    }
}

