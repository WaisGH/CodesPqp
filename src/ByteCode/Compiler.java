package ByteCode;

import Sintatica.Expr;
import Sintatica.Stmt;
import Lexica.Token;
import java.util.List;

public class Compiler implements Expr.Visitor<Void>, Stmt.Visitor<Void> {

    private Chunk currentChunk;

    public Compiler() {
        this.currentChunk = null;
    }

    // Metodo auxiliar para obter a linha do token de forma segura
    private int getCurrentLine(Token token) {
        return (token != null) ? token.line : 0;
    }

    public Chunk compile(List<Stmt> statements) {
        this.currentChunk = new Chunk();

        try {
            for (Stmt stmt : statements) {
                stmt.accept(this);
            }
            // Finaliza o bytecode
            currentChunk.write(OpCode.OP_RETURN, 0);
            return currentChunk;

        } catch (Exception e) {
            System.err.println("Erro de compilação: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    // --- VISITORS DE COMANDO (Stmt) ---

    @Override
    public Void visitInputStmt(Stmt.Input stmt) {
        int line = getCurrentLine(stmt.name);

        // 1. Emite o opcode que lê do console (a VM põe o valor na pilha)
        currentChunk.write(OpCode.OP_INPUT, line);

        // 2. Define o nome da variável global onde o valor será salvo
        int constIndex = currentChunk.addConstant(stmt.name.lexeme);
        currentChunk.write(OpCode.OP_SET_GLOBAL, line);
        currentChunk.write(constIndex, line);

        // 3. OP_SET_GLOBAL mantém o valor na pilha, então fazemos POP para limpar
        currentChunk.write(OpCode.OP_POP, line);

        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        stmt.expression.accept(this);
        currentChunk.write(OpCode.OP_PRINT, 0);
        return null;
    }

    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        // Compila a inicialização ou define como Nulo
        if (stmt.initializer != null) {
            stmt.initializer.accept(this);
        } else {
            currentChunk.write(OpCode.OP_NIL, getCurrentLine(stmt.name));
        }

        // Define a variável global
        int constIndex = currentChunk.addConstant(stmt.name.lexeme);
        currentChunk.write(OpCode.OP_DEFINE_GLOBAL, getCurrentLine(stmt.name));
        currentChunk.write(constIndex, getCurrentLine(stmt.name));
        return null;
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        int line = 0;
        stmt.condition.accept(this); // Avalia condição

        int thenJump = emitJump(OpCode.OP_JUMP_IF_FALSE, line); // Pula se falso
        currentChunk.write(OpCode.OP_POP, line); // Remove a condição da pilha

        stmt.thenBranch.accept(this); // Executa bloco IF

        int elseJump = emitJump(OpCode.OP_JUMP, line); // Pula o ELSE se entrou no IF

        patchJump(thenJump);
        currentChunk.write(OpCode.OP_POP, line); // Pop extra para limpeza se necessário

        if (stmt.elseBranch != null) {
            stmt.elseBranch.accept(this);
        }
        patchJump(elseJump);
        return null;
    }

    @Override
    public Void visitWhileStmt(Stmt.While stmt) {
        int line = 0;
        int loopStart = currentChunk.code.size(); // Marca o início do loop

        stmt.condition.accept(this); // Avalia condição

        int exitJump = emitJump(OpCode.OP_JUMP_IF_FALSE, line); // Sai se falso
        currentChunk.write(OpCode.OP_POP, line);

        stmt.body.accept(this); // Corpo do loop

        emitLoop(loopStart, line); // Volta ao início

        patchJump(exitJump);
        currentChunk.write(OpCode.OP_POP, line);
        return null;
    }

    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        for (Stmt statement : stmt.statements) {
            statement.accept(this);
        }
        return null;
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        stmt.expr.accept(this);
        currentChunk.write(OpCode.OP_POP, 0); // Descarta o resultado (ex: chamada de função sem uso)
        return null;
    }

    // --- VISITORS DE EXPRESSÃO (Expr) ---

    @Override
    public Void visitIncrementoExpr(Expr.Incremento expr) {
        int line = getCurrentLine(expr.name);
        int constIndex = currentChunk.addConstant(expr.name.lexeme);

        // 1. Carrega o valor atual da variável
        currentChunk.write(OpCode.OP_GET_GLOBAL, line);
        currentChunk.write(constIndex, line);

        // 2. Carrega o valor 1
        currentChunk.write(OpCode.OP_CONSTANT, line);
        int oneIdx = currentChunk.addConstant(1);
        currentChunk.write(oneIdx, line);

        // 3. Soma
        currentChunk.write(OpCode.OP_ADD, line);

        // 4. Salva de volta na variável
        currentChunk.write(OpCode.OP_SET_GLOBAL, line);
        currentChunk.write(constIndex, line);

        return null;
    }

    @Override
    public Void visitDecrementoExpr(Expr.Decremento expr) {
        int line = getCurrentLine(expr.name);
        int constIndex = currentChunk.addConstant(expr.name.lexeme);

        // 1. Carrega variável
        currentChunk.write(OpCode.OP_GET_GLOBAL, line);
        currentChunk.write(constIndex, line);

        // 2. Carrega 1
        currentChunk.write(OpCode.OP_CONSTANT, line);
        int oneIdx = currentChunk.addConstant(1);
        currentChunk.write(oneIdx, line);

        // 3. Subtrai
        currentChunk.write(OpCode.OP_SUBTRACT, line);

        // 4. Salva
        currentChunk.write(OpCode.OP_SET_GLOBAL, line);
        currentChunk.write(constIndex, line);

        return null;
    }

    @Override
    public Void visitBinaryExpr(Expr.Binary expr) {
        expr.left.accept(this);
        expr.right.accept(this);
        int line = getCurrentLine(expr.operator);

        switch (expr.operator.type) {
            case PLUS:      currentChunk.write(OpCode.OP_ADD, line); break;
            case MINUS:     currentChunk.write(OpCode.OP_SUBTRACT, line); break;
            case STAR:      currentChunk.write(OpCode.OP_MULTIPLY, line); break;
            case SLASH:     currentChunk.write(OpCode.OP_DIVIDE, line); break;
            case EQUALEQUAL:currentChunk.write(OpCode.OP_EQUAL, line); break;
            case BANGEQUAL: currentChunk.write(OpCode.OP_EQUAL, line); currentChunk.write(OpCode.OP_NOT, line); break;
            case GREATER:   currentChunk.write(OpCode.OP_GREATER, line); break;
            case LESSEQUAL: currentChunk.write(OpCode.OP_GREATER, line); currentChunk.write(OpCode.OP_NOT, line); break;
            case LESS:      currentChunk.write(OpCode.OP_LESS, line); break;
            case GREATEREQUAL:currentChunk.write(OpCode.OP_LESS, line); currentChunk.write(OpCode.OP_NOT, line); break;
            default: throw new RuntimeException("Operador binário desconhecido: " + expr.operator.type);
        }
        return null;
    }

    @Override
    public Void visitLiteralExpr(Expr.Literal expr) {
        if (expr.value == null) {
            currentChunk.write(OpCode.OP_NIL, 0);
        } else if (expr.value instanceof Boolean) {
            currentChunk.write(((Boolean) expr.value) ? OpCode.OP_TRUE : OpCode.OP_FALSE, 0);
        } else {
            int constIndex = currentChunk.addConstant(expr.value);
            currentChunk.write(OpCode.OP_CONSTANT, 0);
            currentChunk.write(constIndex, 0);
        }
        return null;
    }

    @Override
    public Void visitVariableExpr(Expr.Variable expr) {
        int constIndex = currentChunk.addConstant(expr.name.lexeme);
        currentChunk.write(OpCode.OP_GET_GLOBAL, getCurrentLine(expr.name));
        currentChunk.write(constIndex, getCurrentLine(expr.name));
        return null;
    }

    @Override
    public Void visitAssignExpr(Expr.Assign expr) {
        expr.value.accept(this);
        int constIndex = currentChunk.addConstant(expr.name.lexeme);
        currentChunk.write(OpCode.OP_SET_GLOBAL, getCurrentLine(expr.name));
        currentChunk.write(constIndex, getCurrentLine(expr.name));
        return null;
    }

    @Override
    public Void visitUnaryExpr(Expr.Unary expr) {
        expr.right.accept(this);
        int line = getCurrentLine(expr.operator);
        switch (expr.operator.type) {
            case MINUS: currentChunk.write(OpCode.OP_NEGATE, line); break;
            case BANG:  currentChunk.write(OpCode.OP_NOT, line); break;
            default: throw new RuntimeException("Operador unário desconhecido.");
        }
        return null;
    }

    @Override
    public Void visitGroupingExpr(Expr.Grouping expr) {
        expr.expression.accept(this);
        return null;
    }

    // Stubs para funcionalidades ainda não implementadas no compilador mas existentes na interface
    @Override public Void visitCallExpr(Expr.Call expr) { return null; }
    @Override public Void visitFunctionStmt(Stmt.Function stmt) { return null; }
    @Override public Void visitReturnStmt(Stmt.Return stmt) { return null; }
    @Override public Void visitBreakStmt(Stmt.Break stmt) {
        // Break requer gestão de loop stack, simplificado aqui
        return null;
    }
    @Override public Void visitSwitchStmt(Stmt.Switch stmt) { return null; }

    // --- MÉTODOS AUXILIARES DE JUMP (Controle de Fluxo) ---

    private int emitJump(OpCode jumpOpcode, int line) {
        currentChunk.write(jumpOpcode, line);
        currentChunk.write(0xFF, line); // Placeholder High byte
        currentChunk.write(0xFF, line); // Placeholder Low byte
        return currentChunk.code.size() - 2;
    }

    private void patchJump(int offset) {
        int jump = currentChunk.code.size() - offset - 2;
        if (jump > 65535) {
            throw new RuntimeException("Salto muito longo para o bytecode.");
        }
        currentChunk.code.set(offset, (jump >> 8) & 0xFF);
        currentChunk.code.set(offset + 1, jump & 0xFF);
    }

    private void emitLoop(int loopStart, int line) {
        currentChunk.write(OpCode.OP_LOOP, line);
        int offset = currentChunk.code.size() - loopStart + 2;
        if (offset > 65535) {
            throw new RuntimeException("Loop muito longo.");
        }
        currentChunk.write((offset >> 8) & 0xFF, line);
        currentChunk.write(offset & 0xFF, line);
    }
}