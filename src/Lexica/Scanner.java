package Lexica;

import java.util.*;

public class Scanner {
    private final String source;
    private final List<Token> tokens = new ArrayList<>();
    private int start = 0;
    private int current = 0;
    private int line = 1;
    private static final Map<String, TokenType> keywords;

    static {
        keywords = new HashMap<>();
        keywords.put("VAR", TokenType.VAR);
        keywords.put("FUNCAO", TokenType.FUN);
        keywords.put("RETORNA", TokenType.RETURN);
        keywords.put("ESCREVEAI", TokenType.PRINT);
        keywords.put("FAZAVOLTA", TokenType.FOR);
        keywords.put("VOLTAINFINITA", TokenType.WHILE);
        keywords.put("SE", TokenType.IF);
        keywords.put("SENAO", TokenType.ELSE);
        keywords.put("ISSOAI", TokenType.TRUE);
        keywords.put("MENTIRA", TokenType.FALSE);
        keywords.put("NULO", TokenType.NIL);
        keywords.put("ESCOLHEAI", TokenType.SWITCH);
        keywords.put("CASO", TokenType.CASE);
        keywords.put("PADRAO", TokenType.DEFAULT);
        keywords.put("LEIA", TokenType.INPUT);
        keywords.put("PAREI", TokenType.BREAK);

        // Tipos adicionais
        keywords.put("INTEIRO", TokenType.INT);
        keywords.put("QUEBRADO", TokenType.FLOAT);
        keywords.put("BOOL", TokenType.BOOL);
    }

    public Scanner(String source) {
        this.source = source;
    }

    public List<Token> scanTokens() {
        while (!isAtEnd()) {
            start = current;
            scanToken();
        }
        tokens.add(new Token(TokenType.EOF, "", null, line, current));
        return tokens;
    }

    private void scanToken() {
        char c = advance();
        switch (c) {
            case '(': addToken(TokenType.LEFTPAREN); break;
            case ')': addToken(TokenType.RIGHTPAREN); break;
            case '{': addToken(TokenType.LEFTBRACE); break;
            case '}': addToken(TokenType.RIGHTBRACE); break;
            case '[': addToken(TokenType.LEFT_BRACKET); break;
            case ']': addToken(TokenType.RIGHT_BRACKET); break;
            case ',': addToken(TokenType.COMMA); break;
            case ';': addToken(TokenType.SEMICOLON); break;
            case ':': addToken(TokenType.COLON); break;
            case '.': addToken(TokenType.DOT); break;

            // Operadores Aritméticos e Incremento/Decremento
            case '+':
                addToken(match('+') ? TokenType.INCREMENTO : TokenType.PLUS);
                break;
            case '-':
                addToken(match('-') ? TokenType.DECREMENTO : TokenType.MINUS);
                break;
            case '*': addToken(TokenType.STAR); break;
            case '%': addToken(TokenType.PERCENT); break;

            case '/':
                if (match('/')) {
                    // Comentário de linha
                    while (peek() != '\n' && !isAtEnd()) advance();
                } else if (match('*')) {
                    // Comentário de bloco
                    while (!(peek() == '*' && peekNext() == '/') && !isAtEnd()) advance();
                    if (!isAtEnd()) {
                        advance(); // consome *
                        advance(); // consome /
                    }
                } else {
                    addToken(TokenType.SLASH);
                }
                break;

            // Comparadores e Lógicos
            case '!': addToken(match('=') ? TokenType.BANGEQUAL : TokenType.BANG); break;
            case '=': addToken(match('=') ? TokenType.EQUALEQUAL : TokenType.EQUAL); break;
            case '<': addToken(match('=') ? TokenType.LESSEQUAL : TokenType.LESS); break;
            case '>': addToken(match('=') ? TokenType.GREATEREQUAL : TokenType.GREATER); break;
            case '&': if (match('&')) addToken(TokenType.AND); break;
            case '|': if (match('|')) addToken(TokenType.OR); break;

            // Espaços em branco
            case ' ': case '\r': case '\t': break;
            case '\n': line++; break;

            // String
            case '"': string(); break;

            default:
                if (isDigit(c)) {
                    number();
                } else if (isAlpha(c)) {
                    identifier();
                } else {
                    System.err.println("Caractere inválido na linha " + line + ": " + c);
                }
                break;
        }
    }

    // --- MÉTODOS AUXILIARES ---

    private void identifier() {
        while (isAlphaNumeric(peek())) {
            advance();
        }
        String text = source.substring(start, current);
        TokenType type = keywords.get(text);
        if (type == null) type = TokenType.IDENTIFIER;
        addToken(type);
    }

    private void number() {
        while (isDigit(peek())) advance();

        // Parte fracionária
        if (peek() == '.' && isDigit(peekNext())) {
            advance(); // Consome o "."
            while (isDigit(peek())) advance();

            String text = source.substring(start, current);
            addToken(TokenType.NUMBER, Double.parseDouble(text));
        } else {
            String text = source.substring(start, current);
            addToken(TokenType.NUMBER, Integer.parseInt(text));
        }
    }

    private void string() {
        while (peek() != '"' && !isAtEnd()) {
            if (peek() == '\n') line++;
            advance();
        }

        if (isAtEnd()) {
            System.err.println("String não terminada na linha " + line);
            return;
        }

        advance(); // O " de fechamento
        String value = source.substring(start + 1, current - 1);
        addToken(TokenType.STRING, value);
    }

    private boolean match(char expected) {
        if (isAtEnd()) return false;
        if (source.charAt(current) != expected) return false;
        current++;
        return true;
    }

    private char peek() {
        if (isAtEnd()) return '\0';
        return source.charAt(current);
    }

    private char peekNext() {
        if (current + 1 >= source.length()) return '\0';
        return source.charAt(current + 1);
    }

    private boolean isAlpha(char c) {
        return (c >= 'a' && c <= 'z') ||
                (c >= 'A' && c <= 'Z') ||
                c == '_';
    }

    private boolean isAlphaNumeric(char c) {
        return isAlpha(c) || isDigit(c);
    }

    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private char advance() {
        return source.charAt(current++);
    }

    private boolean isAtEnd() {
        return current >= source.length();
    }

    private void addToken(TokenType type) {
        addToken(type, null);
    }

    private void addToken(TokenType type, Object literal) {
        String lexeme = source.substring(start, current);
        tokens.add(new Token(type, lexeme, literal, line, start));
    }
}