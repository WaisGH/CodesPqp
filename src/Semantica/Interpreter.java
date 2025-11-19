package Semantica;

import Lexica.Scanner;
import Lexica.Token;
import Lexica.TokenType;
import Sintatica.Expr;
import Sintatica.Parser;
import Sintatica.Stmt;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

// Classe responsável por interpretar as expressões e comandos da linguagem.
public class Interpreter implements Expr.Visitor<Object>, Stmt.Visitor<Void> {

    // Ambiente atual com variáveis e seus valores
    private Environment environment = new Environment();

    // Leitor de entrada padrão
    private final java.util.Scanner consoleInput = new java.util.Scanner(System.in);

    public void interpret(List<Stmt> statements) {
        try {
            for (Stmt statement : statements) {
                execute(statement);
            }
        } catch (RuntimeException error) {
            System.err.println("Erro de execução: " + error.getMessage());
        }
    }

    private Object evaluate(Expr expr) {
        return expr.accept(this);
    }

    private void execute(Stmt stmt) {
        stmt.accept(this);
    }

    void executeBlock(List<Stmt> statements, Environment environment) {
        Environment previous = this.environment;
        try {
            this.environment = environment;
            for (Stmt statement : statements) {
                execute(statement);
            }
        } finally {
            this.environment = previous;
        }
    }

    // --- VISITORS DE EXPRESSÃO (Expr) ---

    @Override
    public Object visitBinaryExpr(Expr.Binary expr) {
        Object left = evaluate(expr.left);
        Object right = evaluate(expr.right);

        switch (expr.operator.type) {
            case PLUS:
                if (left instanceof Integer && right instanceof Integer) return (Integer) left + (Integer) right;
                if (left instanceof Double && right instanceof Double) return (Double) left + (Double) right;
                if (left instanceof Integer && right instanceof Double) return (Integer) left + (Double) right;
                if (left instanceof Double && right instanceof Integer) return (Double) left + (Integer) right;
                if (left instanceof String || right instanceof String) return stringify(left) + stringify(right);
                throw new RuntimeException("Operadores '+' exigem números ou strings.");
            case MINUS:
                checkNumberOperands(expr.operator, left, right);
                if (left instanceof Integer && right instanceof Integer) return (Integer) left - (Integer) right;
                return toDouble(left) - toDouble(right);
            case STAR:
                checkNumberOperands(expr.operator, left, right);
                if (left instanceof Integer && right instanceof Integer) return (Integer) left * (Integer) right;
                return toDouble(left) * toDouble(right);
            case SLASH:
                checkNumberOperands(expr.operator, left, right);
                if (toDouble(right) == 0) throw new RuntimeException("Divisão por zero.");
                if (left instanceof Integer && right instanceof Integer) return (Integer) left / (Integer) right;
                return toDouble(left) / toDouble(right);
            case PERCENT:
                checkNumberOperands(expr.operator, left, right);
                if (left instanceof Integer && right instanceof Integer) return (Integer) left % (Integer) right;
                return toDouble(left) % toDouble(right);
            case GREATER:
                checkNumberOperands(expr.operator, left, right);
                return toDouble(left) > toDouble(right);
            case GREATEREQUAL:
                checkNumberOperands(expr.operator, left, right);
                return toDouble(left) >= toDouble(right);
            case LESS:
                checkNumberOperands(expr.operator, left, right);
                return toDouble(left) < toDouble(right);
            case LESSEQUAL:
                checkNumberOperands(expr.operator, left, right);
                return toDouble(left) <= toDouble(right);
            case BANGEQUAL:
                return !isEqual(left, right);
            case EQUALEQUAL:
                return isEqual(left, right);
            case AND:
                return isTruthy(left) ? isTruthy(right) : false;
            case OR:
                return isTruthy(left) ? true : isTruthy(right);
            default:
                throw new RuntimeException("Operador desconhecido: " + expr.operator.type);
        }
    }

    @Override
    public Object visitGroupingExpr(Expr.Grouping expr) {
        return evaluate(expr.expression);
    }

    @Override
    public Object visitLiteralExpr(Expr.Literal expr) {
        return expr.value;
    }

    @Override
    public Object visitUnaryExpr(Expr.Unary expr) {
        Object right = evaluate(expr.right);
        switch (expr.operator.type) {
            case MINUS:
                checkNumberOperand(expr.operator, right);
                if (right instanceof Integer) return -(Integer) right;
                return -(Double) right;
            case BANG:
                return !isTruthy(right);
        }
        return null;
    }

    @Override
    public Object visitVariableExpr(Expr.Variable expr) {
        return environment.get(expr.name);
    }

    @Override
    public Object visitAssignExpr(Expr.Assign expr) {
        Object value = evaluate(expr.value);
        environment.assign(expr.name, value);
        return value;
    }

    @Override
    public Object visitCallExpr(Expr.Call expr) {
        Object callee = evaluate(expr.callee);

        List<Object> arguments = new java.util.ArrayList<>();
        for (Expr argument : expr.arguments) {
            arguments.add(evaluate(argument));
        }

        if (!(callee instanceof LoxCallable)) {
            throw new RuntimeException("Só é possível chamar funções.");
        }

        LoxCallable function = (LoxCallable) callee;
        if (arguments.size() != function.arity()) {
            throw new RuntimeException("Esperado " + function.arity() + " argumentos, mas obteve " + arguments.size() + ".");
        }

        return function.call(this, arguments);
    }

    // --- INCREMENTO & DECREMENTO ---

    @Override
    public Object visitIncrementoExpr(Expr.Incremento expr) {
        Object value = environment.get(expr.name);

        if (value instanceof Integer) {
            int num = (Integer) value;
            if (expr.prefix) { // ++i
                environment.assign(expr.name, num + 1);
                return num + 1;
            } else { // i++
                environment.assign(expr.name, num + 1);
                return num;
            }
        } else if (value instanceof Double) {
            double num = (Double) value;
            if (expr.prefix) {
                environment.assign(expr.name, num + 1.0);
                return num + 1.0;
            } else {
                environment.assign(expr.name, num + 1.0);
                return num;
            }
        }
        throw new RuntimeException("Operando de incremento deve ser um número.");
    }

    @Override
    public Object visitDecrementoExpr(Expr.Decremento expr) {
        Object value = environment.get(expr.name);

        if (value instanceof Integer) {
            int num = (Integer) value;
            if (expr.prefix) { // --i
                environment.assign(expr.name, num - 1);
                return num - 1;
            } else { // i--
                environment.assign(expr.name, num - 1);
                return num;
            }
        } else if (value instanceof Double) {
            double num = (Double) value;
            if (expr.prefix) {
                environment.assign(expr.name, num - 1.0);
                return num - 1.0;
            } else {
                environment.assign(expr.name, num - 1.0);
                return num;
            }
        }
        throw new RuntimeException("Operando de decremento deve ser um número.");
    }

    // --- VISITORS DE COMANDO (Stmt) ---

    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        executeBlock(stmt.statements, new Environment(environment));
        return null;
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        evaluate(stmt.expr);
        return null;
    }

    @Override
    public Void visitFunctionStmt(Stmt.Function stmt) {
        LoxFunction function = new LoxFunction(stmt, environment);
        environment.define(stmt.name.lexeme, function);
        return null;
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        if (isTruthy(evaluate(stmt.condition))) {
            execute(stmt.thenBranch);
        } else if (stmt.elseBranch != null) {
            execute(stmt.elseBranch);
        }
        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        Object value = evaluate(stmt.expression);
        System.out.println(stringify(value));
        return null;
    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {
        Object value = null;
        if (stmt.value != null) value = evaluate(stmt.value);
        throw new ReturnException(value);
    }

    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        Object value = null;
        if (stmt.initializer != null) {
            value = evaluate(stmt.initializer);
        }
        environment.define(stmt.name.lexeme, value);
        return null;
    }

    @Override
    public Void visitWhileStmt(Stmt.While stmt) {
        while (isTruthy(evaluate(stmt.condition))) {
            try {
                execute(stmt.body);
            } catch (BreakException e) {
                break;
            }
        }
        return null;
    }

    @Override
    public Void visitBreakStmt(Stmt.Break stmt) {
        throw new BreakException();
    }

    @Override
    public Void visitSwitchStmt(Stmt.Switch stmt) {
        Object value = evaluate(stmt.expr);
        boolean matchFound = false;

        if (stmt.cases != null) {
            for (Stmt.Case caso : stmt.cases) {
                if (matchFound || isEqual(value, evaluate(caso.value))) {
                    matchFound = true;
                    try {
                        execute(caso.stmt);
                    } catch (BreakException e) {
                        return null;
                    }
                }
            }
        }

        if (!matchFound && stmt.defaultCase != null) {
            try {
                execute(stmt.defaultCase.stmt);
            } catch (BreakException e) {
                return null;
            }
        }
        return null;
    }

    @Override
    public Void visitInputStmt(Stmt.Input stmt) {
        System.out.print("> ");
        if (consoleInput.hasNextLine()) {
            String line = consoleInput.nextLine();
            Object valor;
            try {
                valor = Integer.parseInt(line);
            } catch (NumberFormatException e1) {
                try {
                    valor = Double.parseDouble(line);
                } catch (NumberFormatException e2) {
                    valor = line;
                }
            }
            environment.assign(stmt.name, valor);
        }
        return null;
    }

    // --- Helpers ---

    private void checkNumberOperand(Token operator, Object operand) {
        if (operand instanceof Double || operand instanceof Integer) return;
        throw new RuntimeException(operator + " O operando deve ser um número.");
    }

    private void checkNumberOperands(Token operator, Object left, Object right) {
        if ((left instanceof Double || left instanceof Integer) && (right instanceof Double || right instanceof Integer)) return;
        throw new RuntimeException(operator + " Os operandos devem ser números.");
    }

    private double toDouble(Object o) {
        if (o instanceof Double) return (Double) o;
        if (o instanceof Integer) return ((Integer) o).doubleValue();
        throw new RuntimeException("Esperado um número.");
    }

    private boolean isTruthy(Object object) {
        if (object == null) return false;
        if (object instanceof Boolean) return (boolean) object;
        return true;
    }

    private boolean isEqual(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null) return false;
        return a.equals(b);
    }

    private String stringify(Object object) {
        if (object == null) return "nulo";
        if (object instanceof Double) {
            String text = object.toString();
            if (text.endsWith(".0")) {
                text = text.substring(0, text.length() - 2);
            }
            return text;
        }
        return object.toString();
    }

    // Classes auxiliares internas

    public static class BreakException extends RuntimeException { }

    public static class ReturnException extends RuntimeException {
        final Object value;
        ReturnException(Object value) {
            super(null, null, false, false);
            this.value = value;
        }
    }

    public interface LoxCallable {
        int arity();
        Object call(Interpreter interpreter, List<Object> arguments);
    }

    public static class LoxFunction implements LoxCallable {
        private final Stmt.Function declaration;
        private final Environment closure;

        LoxFunction(Stmt.Function declaration, Environment closure) {
            this.closure = closure;
            this.declaration = declaration;
        }

        @Override
        public int arity() { return declaration.parameters.size(); }

        @Override
        public Object call(Interpreter interpreter, List<Object> arguments) {
            Environment environment = new Environment(closure);
            for (int i = 0; i < declaration.parameters.size(); i++) {
                environment.define(declaration.parameters.get(i).lexeme, arguments.get(i));
            }
            try {
                interpreter.executeBlock(declaration.body, environment);
            } catch (ReturnException returnValue) {
                return returnValue.value;
            }
            return null;
        }
    }

    public static class Environment {
        final Map<String, Object> values = new HashMap<>();
        final Environment enclosing;

        Environment() { enclosing = null; }
        Environment(Environment enclosing) { this.enclosing = enclosing; }

        Object get(Token name) {
            if (values.containsKey(name.lexeme)) {
                return values.get(name.lexeme);
            }
            if (enclosing != null) return enclosing.get(name);
            throw new RuntimeException("Variável indefinida '" + name.lexeme + "'.");
        }

        void assign(Token name, Object value) {
            if (values.containsKey(name.lexeme)) {
                values.put(name.lexeme, value);
                return;
            }
            if (enclosing != null) {
                enclosing.assign(name, value);
                return;
            }
            throw new RuntimeException("Variável indefinida '" + name.lexeme + "'.");
        }

        // Sobrecarga para aceitar lexeme direto (para Incremento/Decremento)
        void assign(String name, Object value) {
            if (values.containsKey(name)) {
                values.put(name, value);
                return;
            }
            if (enclosing != null) {
                enclosing.assign(name, value);
                return;
            }
            throw new RuntimeException("Variável indefinida '" + name + "'.");
        }

        void define(String name, Object value) {
            values.put(name, value);
        }
    }
}