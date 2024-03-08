package interpreter;

import java.io.*;
import java.util.Random;
import java.util.function.Function;

import parser.ParserWrapper;
import ast.*;
import java.util.HashMap;
import java.util.Map;

public class Interpreter {

    // Process return codes
    public static final int EXIT_SUCCESS = 0;
    public static final int EXIT_PARSING_ERROR = 1;
    public static final int EXIT_STATIC_CHECKING_ERROR = 2;
    public static final int EXIT_DYNAMIC_TYPE_ERROR = 3;
    public static final int EXIT_NIL_REF_ERROR = 4;
    public static final int EXIT_QUANDARY_HEAP_OUT_OF_MEMORY_ERROR = 5;
    public static final int EXIT_DATA_RACE_ERROR = 6;
    public static final int EXIT_NONDETERMINISM_ERROR = 7;
    //private static HashMap<String, Long> variables = new HashMap<String, Long>();
    private static HashMap<String, FuncDef> function = new HashMap<String, FuncDef>();
    static private Interpreter interpreter;
    static private boolean returnFlag = false;
    public static Interpreter getInterpreter() {
        return interpreter;
    }

    public static void main(String[] args) {
        String gcType = "NoGC"; // default for skeleton, which only supports NoGC
        long heapBytes = 1 << 14;
        int i = 0;
        String filename;
        long quandaryArg;
        try {
            for (; i < args.length; i++) {
                String arg = args[i];
                if (arg.startsWith("-")) {
                    if (arg.equals("-gc")) {
                        gcType = args[i + 1];
                        i++;
                    } else if (arg.equals("-heapsize")) {
                        heapBytes = Long.valueOf(args[i + 1]);
                        i++;
                    } else {
                        throw new RuntimeException("Unexpected option " + arg);
                    }
                } else {
                    if (i != args.length - 2) {
                        throw new RuntimeException("Unexpected number of arguments");
                    }
                    break;
                }
            }
            filename = args[i];
            quandaryArg = Long.valueOf(args[i + 1]);
        } catch (Exception ex) {
            System.out.println("Expected format: quandary [OPTIONS] QUANDARY_PROGRAM_FILE INTEGER_ARGUMENT");
            System.out.println("Options:");
            System.out.println("  -gc (MarkSweep|Explicit|NoGC)");
            System.out.println("  -heapsize BYTES");
            System.out.println("BYTES must be a multiple of the word size (8)");
            return;
        }

        Program astRoot = null;
        Reader reader;
        try {
            reader = new BufferedReader(new FileReader(filename));
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        try {
            astRoot = ParserWrapper.parse(reader);
        } catch (Exception ex) {
            ex.printStackTrace();
            Interpreter.fatalError("Uncaught parsing error: " + ex, Interpreter.EXIT_PARSING_ERROR);
        }
        //astRoot.println(System.out);
        interpreter = new Interpreter(astRoot);
        interpreter.initMemoryManager(gcType, heapBytes);
       
        String returnValueAsString = interpreter.executeRoot(astRoot, quandaryArg).toString();
        System.out.println("Interpreter returned " + returnValueAsString);
    }

    final Program astRoot;
    final Random random;

    private Interpreter(Program astRoot) {
        this.astRoot = astRoot;
        this.random = new Random();
    }

    void initMemoryManager(String gcType, long heapBytes) {
        if (gcType.equals("Explicit")) {
            throw new RuntimeException("Explicit not implemented");            
        } else if (gcType.equals("MarkSweep")) {
            throw new RuntimeException("MarkSweep not implemented");            
        } else if (gcType.equals("RefCount")) {
            throw new RuntimeException("RefCount not implemented");            
        } else if (gcType.equals("NoGC")) {
            // Nothing to do
        }
    }
    
    Object executeRoot(Program astRoot, long arg) {
        return evaluate(astRoot.getFuncDefList(),arg);
    }
  
   
    Object evaluate(FuncDefList funcDefList,long arg){

        return evaluate(funcDefList.getFuncDef(),arg);
    }
    Object evaluate(FuncDef funcDef, long arg) {
        evaluate(funcDef.getFormalDeclList(), arg);
    
        return evaluate(funcDef.getStmtList());
    }
    Object evaluate(StmtList stmtList){
        Object stmt = evaluate(stmtList.getStmt());
        if (returnFlag) return stmt;
        while (stmtList.getStmtList() != null)
        {
            stmtList = stmtList.getStmtList();
            stmt = evaluate(stmtList.getStmt());
            if(returnFlag == true)
            {
                break;
            }
        }
        return stmt;
    }
    Object evaluate(Type type){
        return evaluateType(type);
    }
    void evaluate(NeFormalDeclList neFormalDeclList, long arg){
    String parameterName = neFormalDeclList.getVarDecl().getId().toString();
    variables.put(parameterName, arg);
}

    Object evaluate(ExprList exprList){
       if (exprList.getneExprList() != null){
           return evaluate(exprList.getneExprList());   
       }
       else{
        return null;
       }
    }
    Object evaluate(NeExprList neExprList){
        Object firstExprValue = evaluate(neExprList.getExpr());
    
        if (neExprList.getneExprList() != null) {
            return evaluate(neExprList.getneExprList());
        }
        return firstExprValue; 
    }
    void evaluate(FormalDeclList formalDeclList, long arg){
        evaluate(formalDeclList.getNeFormalDeclListNode(), arg);
    }

    Boolean evaluateCond(Cond cond){
           switch(cond.getConditionOperator()){
                case Cond.LESSTHANOREQUAL: return (long)evaluate(cond.getExpr1()) <= (long)evaluate(cond.getExpr2());
                case Cond.GREATERTHANOREQUAL: return (long)evaluate(cond.getExpr1()) >= (long)evaluate(cond.getExpr2());
                case Cond.EQUALTO: return (long)evaluate(cond.getExpr1()) == (long)evaluate(cond.getExpr2());
                case Cond.NOTEQUALTO: return (long)evaluate(cond.getExpr1()) != (long)evaluate(cond.getExpr2());
                case Cond.LESSTHAN: return (long)evaluate(cond.getExpr1()) < (long)evaluate(cond.getExpr2());
                case Cond.GREATERTHAN: return (long)evaluate(cond.getExpr1()) > (long)evaluate(cond.getExpr2());
                case Cond.AND: return evaluateCond((Cond)cond.getExpr1()) && evaluateCond((Cond)cond.getExpr2());
                case Cond.OR: return evaluateCond((Cond)cond.getExpr1()) || evaluateCond((Cond)cond.getExpr2());
                case Cond.NOT: return !(evaluateCond((Cond)cond.getExpr1()));

                default: throw new RuntimeException("Unhandled operator");
              
           }
    
    }
    Object evaluate(Stmt stmt){
        if (stmt instanceof DeclarationStmt){
            DeclarationStmt declStmt = (DeclarationStmt)stmt;
            String varName = declStmt.getVarDecl().getId().toString();
            Object value = evaluate(declStmt.getExpression());
            variables.put(varName, (Long)value);
            return value;
        }
        else if (stmt instanceof AssignmentStmt) {
            AssignmentStmt assignStmt = (AssignmentStmt)stmt;
            String varName = assignStmt.getIdNode().toString();
            Object value = evaluate(assignStmt.getExprNode());
            // Check if variable is declared, if not, declare it
            if (!variables.containsKey(varName)) {
                variables.put(varName, (Long)value);
            }
            // Assign value to variable
            variables.put(varName, (Long)value);
            return value;
        } else if (stmt instanceof IfStatement) {
            IfStatement ifStatement = (IfStatement)stmt;
            Boolean condition = evaluateCond(ifStatement.getCondNode());
            Object value = null;
            if (condition){
                value = evaluate(ifStatement.getStmtNode());
            }
            return value;
        }
         
        else if (stmt instanceof IfElseStmt){
            IfElseStmt ifElseStmt = (IfElseStmt)stmt;
        
            Boolean condition = evaluateCond(ifElseStmt.getCondNode());
            System.out.println(condition);
            
            Object value = null;
            if (condition){
                value = evaluate(ifElseStmt.getStmtNode1());
            } else {
                value = evaluate(ifElseStmt.getStmtNode2());
            }
            return value;
        }
        
         else if (stmt instanceof WhileStmt) {
            WhileStmt whileStmt = (WhileStmt)stmt;
            Boolean condition = evaluateCond(whileStmt.getCondNode());
            Object value = evaluate(whileStmt.getStmtNode());

            while (!condition) {
                value = evaluate(whileStmt.getStmtNode());
                condition = evaluateCond(whileStmt.getCondNode());
            }
            return value;
        }
        else if (stmt instanceof CallStmt) {
            CallStmt callStmt = (CallStmt)stmt;
            Object value = evaluate(callStmt.getExprList());
            return value;
        }
        
         else if (stmt instanceof PrintStmt) {
            PrintStmt printStmt = (PrintStmt)stmt;
            Object value = evaluate(printStmt.getExpression());
            System.out.println(value);
            return value;
        }else if (stmt instanceof ReturnStmt) {
            ReturnStmt returnStmt = (ReturnStmt)stmt;
            returnFlag = true;
            Object value = evaluate(returnStmt.getExpression());
            return value;
        }else if (stmt instanceof BlockStmt) {
            BlockStmt blockStmt = (BlockStmt)stmt;
            Object value = evaluate(blockStmt.getStmtListNode());
            return value;
        }
         else {
            throw new RuntimeException("Unhandled Stmt type");
        }
    }

    Object evaluate(Expr expr) {
        if (expr instanceof ConstExpr) {
            return ((ConstExpr)expr).getValue();
        } else if (expr instanceof BinaryExpr) {
            BinaryExpr binaryExpr = (BinaryExpr)expr;
            switch (binaryExpr.getOperator()) {
                case BinaryExpr.PLUS: return (Long)evaluate(binaryExpr.getLeftExpr()) + (Long)evaluate(binaryExpr.getRightExpr());
                case BinaryExpr.MINUS: return (Long)evaluate(binaryExpr.getLeftExpr()) - (Long)evaluate(binaryExpr.getRightExpr());
                case BinaryExpr.TIMES: return (Long)evaluate(binaryExpr.getLeftExpr()) * (Long)evaluate(binaryExpr.getRightExpr());
                default: throw new RuntimeException("Unhandled operator");
            }
        } else if (expr instanceof UnaryMinusExpr){
            Expr child = ((UnaryMinusExpr)expr).getExpr();
            long value = (long) evaluate(child);
            long newValue = -value;
            return newValue;
        } else if(expr instanceof IdentExpr){            
            return variables.get(((IdentExpr)expr).getIdent());
        } else if(expr instanceof CallExpr){
            CallExpr callExpr = (CallExpr)expr;
            String functionName = callExpr.getId().getIdent();
            Expr firstParameter = callExpr.getExprList().getneExprList().getExpr();
            ExprList restParameter = callExpr.getExprList();
            if (firstParameter == null){
                return evaluate(function.get(functionName).getStmtList());
            }
            else if (restParameter == null){
                evaluate(firstParameter);
                return evaluate(function.get(functionName).getStmtList());
            }
            else{
                evaluate(firstParameter);
                evaluate(restParameter);
                return evaluate(function.get(functionName).getStmtList());
            }
           
            
        
           // return evaluate(function.get(functionName).getStmtList());
        }
        else {
            throw new RuntimeException("Unhandled Expr type");
        }
    }
    Object evaluateType(Type type){
        return type.getType();
    }
	public static void fatalError(String message, int processReturnCode) {
        System.out.println(message);
        System.exit(processReturnCode);
	}
}
