package analyzer.visitors;

import analyzer.ast.*;
import com.sun.org.apache.xpath.internal.operations.Bool;
import javafx.scene.control.Tab;
import org.omg.PortableInterceptor.SYSTEM_EXCEPTION;
import sun.awt.Symbol;

import java.awt.*;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.Vector;


/**
 * Created: 19-02-15
 * Last Changed: 20-10-6
 * Author: Félix Brunet & Doriane Olewicki
 *
 * Description: Ce visiteur explore l'AST et génère un code intermédiaire.
 */

public class IntermediateCodeGenVisitor implements ParserVisitor {

    //le m_writer est un Output_Stream connecter au fichier "result". c'est donc ce qui permet de print dans les fichiers
    //le code généré.
    private final PrintWriter m_writer;

    public IntermediateCodeGenVisitor(PrintWriter writer) {
        m_writer = writer;
    }
    public HashMap<String, VarType> SymbolTable = new HashMap<>();
    public Vector<Integer> intValues = new Vector<>();


    private int id = 0;
    private int label = 0;
    /*
    génère une nouvelle variable temporaire qu'il est possible de print
    À noté qu'il serait possible de rentrer en conflit avec un nom de variable définit dans le programme.
    Par simplicité, dans ce tp, nous ne concidérerons pas cette possibilité, mais il faudrait un générateur de nom de
    variable beaucoup plus robuste dans un vrai compilateur.
     */
    private String genId() {
        return "_t" + id++;
    }

    //génère un nouveau Label qu'il est possible de print.
    private String genLabel() { return "_L" + label++; }

    @Override
    public Object visit(SimpleNode node, Object data) {
        return data;
    }

    @Override
    public Object visit(ASTProgram node, Object data)  {
        node.childrenAccept(this, data);
        return null;
    }

    /*
    Code fournis pour remplir la table de symbole.
    Les déclarations ne sont plus utile dans le code à trois adresse.
    elle ne sont donc pas concervé.
     */
    @Override
    public Object visit(ASTDeclaration node, Object data) {
        ASTIdentifier id = (ASTIdentifier) node.jjtGetChild(0);
        VarType t;
        if(node.getValue().equals("bool")) {
            t = VarType.Bool;
        } else {
            t = VarType.Number;
        }
        SymbolTable.put(id.getValue(), t);
        return null;
    }

    @Override
    public Object visit(ASTBlock node, Object data) {
        String label = genLabel();
        data = label;
        node.childrenAccept(this, data);
        m_writer.print(label + "\n");


        return null;
    }

    @Override
    public Object visit(ASTStmt node, Object data) {
        return node.jjtGetChild(0).jjtAccept(this, data); }

    /*
    le If Stmt doit vérifier s'il à trois enfants pour savoir s'il s'agit d'un "if-then" ou d'un "if-then-else".
     */
    @Override
    public Object visit(ASTIfStmt node, Object data) {
        for (int i = 0; i < node.jjtGetNumChildren(); i++) {
            node.jjtGetChild(i).jjtAccept(this, data);
        }
        return null;
    }

    @Override
    public Object visit(ASTWhileStmt node, Object data) {
        for (int i = 0; i < node.jjtGetNumChildren(); i++) {
            node.jjtGetChild(i).jjtAccept(this, data);
        }
        return null;
    }


    @Override
    public Object visit(ASTAssignStmt node, Object data) {

        String firstLabel = (String) data;
        String id = ((ASTIdentifier) node.jjtGetChild(0)).getValue();


        if(SymbolTable.get(id) == VarType.Number) {
            String expr = ((String) node.jjtGetChild(1).jjtAccept(this, data));
            m_writer.print(id + " = " + expr + "\n");
            if(SymbolTable.size() > 1 && SymbolTable.size() > label) {
                m_writer.print(genLabel() + "\n");
            }
        } else {

            BoolLabel label = new BoolLabel(genLabel(), genLabel());
            data = label;
            String child = (String) node.jjtGetChild(1).jjtAccept(this, data);

            if(child != null && SymbolTable.get(child) == VarType.Bool && SymbolTable.size() < 3) {
                m_writer.print("if " + child + " == 1 goto " + label.lTrue + "\n");
                m_writer.print("goto " + label.lFalse + "\n");
            }

            m_writer.print(label.lTrue + "\n");
            m_writer.print(id + " = 1" + "\n");
            m_writer.print("goto " + firstLabel +"\n");
            m_writer.print(label.lFalse + "\n");
            m_writer.print(id + " = 0" + "\n");

        }

        return null;
    }



    @Override
    public Object visit(ASTExpr node, Object data){
        return node.jjtGetChild(0).jjtAccept(this, data);
    }

    //Expression arithmétique
    /*
    Les expressions arithmétique add et mult fonctionne exactement de la même manière. c'est pourquoi
    il est plus simple de remplir cette fonction une fois pour avoir le résultat pour les deux noeuds.

    On peut bouclé sur "ops" ou sur node.jjtGetNumChildren(),
    la taille de ops sera toujours 1 de moins que la taille de jjtGetNumChildren
     */
    public Object codeExtAddMul(SimpleNode node, Object data, Vector<String> ops) {
        if(node.jjtGetNumChildren() == 1) {
            return node.jjtGetChild(0).jjtAccept(this, data);
        } else {
            String id = genId();

            String first = (String) node.jjtGetChild(0).jjtAccept(this, data);
            String second = (String) node.jjtGetChild(1).jjtAccept(this, data);

            m_writer.print(id + " = " + first + " " + ops.firstElement() + " " + second + "\n");
            return id;
        }
    }

    @Override
    public Object visit(ASTAddExpr node, Object data) {
        return codeExtAddMul(node, data, node.getOps());
    }

    @Override
    public Object visit(ASTMulExpr node, Object data) {
        return codeExtAddMul(node, data, node.getOps());
    }

    //UnaExpr est presque pareil au deux précédente. la plus grosse différence est qu'il ne va pas
    //chercher un deuxième noeud enfant pour avoir une valeur puisqu'il s'agit d'une opération unaire.
    @Override
    public Object visit(ASTUnaExpr node, Object data) {
        if(node.getOps().size() != 0) {
            String child = (String) node.jjtGetChild(0).jjtAccept(this, data);
            String id = genId();
            if(node.getOps().size() == 1) {
                m_writer.print(id + " = " + node.getOps().firstElement() + " " + child + "\n");
                return id;
            } else {
                int index = 0;
                for(Object op : node.getOps()) {
                    m_writer.print(id + " = " + op + " " + child + "\n");
                    child = id;
                    index++;
                    if(index < node.getOps().size()) {
                        id = genId();
                    }
                }
            }
            return id;
        } else {
            return node.jjtGetChild(0).jjtAccept(this, data);
        }

    }

    //expression logique
    @Override
    public Object visit(ASTBoolExpr node, Object data) {
        if(node.jjtGetNumChildren() > 1) {
            String firstChild = (String) node.jjtGetChild(0).jjtAccept(this, data);
            String secondChild = (String) node.jjtGetChild(1).jjtAccept(this, data);
            if(SymbolTable.get(firstChild) == VarType.Bool && SymbolTable.get(secondChild) == VarType.Bool) {
                if(node.getOps().equals("&&")) {
                    BoolLabel label = (BoolLabel) data;
                    BoolLabel firstLabel = new BoolLabel(genLabel(), label.lFalse);
                    BoolLabel secondLabel = new BoolLabel(label.lTrue, label.lFalse);

                    m_writer.print("if " + firstChild + " == 1 goto " + firstLabel.lTrue + "\n");
                    m_writer.print("goto " + firstLabel.lFalse + "\n");
                    m_writer.print(firstLabel.lTrue + "\n");

                    m_writer.print("if " + secondChild + " == 1 goto " + secondLabel.lTrue + "\n");
                    m_writer.print("goto " + secondLabel.lFalse + "\n");
                } else if(node.getOps().equals("||")) {
                    BoolLabel label = (BoolLabel) data;
                    BoolLabel firstLabel = new BoolLabel(label.lTrue, genLabel());
                    BoolLabel secondLabel = new BoolLabel(label.lTrue, label.lFalse);

                    m_writer.print("if " + firstChild + " == 1 goto " + firstLabel.lTrue + "\n");
                    m_writer.print("goto " + firstLabel.lFalse + "\n");
                    m_writer.print(firstLabel.lFalse + "\n");

                    m_writer.print("if " + secondChild + " == 1 goto " + secondLabel.lTrue + "\n");
                    m_writer.print("goto " + secondLabel.lFalse + "\n");
                }
            } else {

            }
        }
        return node.jjtGetChild(0).jjtAccept(this, data);
    }


    @Override
    public Object visit(ASTCompExpr node, Object data) {
//        for (int i = 0; i < node.jjtGetNumChildren(); i++) {
//            node.jjtGetChild(i).jjtAccept(this, data);
//        }
        return node.jjtGetChild(0).jjtAccept(this, data);
    }


    /*
    Même si on peut y avoir un grand nombre d'opération, celle-ci s'annullent entre elle.
    il est donc intéressant de vérifier si le nombre d'opération est pair ou impaire.
    Si le nombre d'opération est pair, on peut simplement ignorer ce noeud.
     */
    @Override
    public Object visit(ASTNotExpr node, Object data) {
        return node.jjtGetChild(0).jjtAccept(this, data);
    }

    @Override
    public Object visit(ASTGenValue node, Object data) {
        return node.jjtGetChild(0).jjtAccept(this, data);
    }

    /*
    BoolValue ne peut pas simplement retourné sa valeur à son parent contrairement à GenValue et IntValue,
    Il doit plutôt généré des Goto direct, selon sa valeur.
     */
    @Override
    public Object visit(ASTBoolValue node, Object data) {
        if(data != null) {
            BoolLabel label = (BoolLabel) data;
            if(node.getValue() == true) {
                m_writer.print("goto " + label.lTrue + "\n");
            } else {
               m_writer.print("goto " + label.lFalse + "\n");
            }
       }

        return node.getValue().toString();
    }


    /*
    si le type de la variable est booléenne, il faudra généré des goto ici.
    le truc est de faire un "if value == 1 goto Label".
    en effet, la structure "if valeurBool goto Label" n'existe pas dans la syntaxe du code à trois adresse.
     */
    @Override
    public Object visit(ASTIdentifier node, Object data) {


        return node.getValue();
    }

    @Override
    public Object visit(ASTIntValue node, Object data) {
        return Integer.toString(node.getValue());
    }


    @Override
    public Object visit(ASTSwitchStmt node, Object data) {
        for (int i = 0; i < node.jjtGetNumChildren(); i++) {
            node.jjtGetChild(i).jjtAccept(this, data);
        }
        return null;
    }

    @Override
    public Object visit(ASTCaseStmt node, Object data) {
        for (int i = 0; i < node.jjtGetNumChildren(); i++) {
            node.jjtGetChild(i).jjtAccept(this, data);
        }
        return null;
    }

    @Override
    public Object visit(ASTDefaultStmt node, Object data) {
        for (int i = 0; i < node.jjtGetNumChildren(); i++) {
            node.jjtGetChild(i).jjtAccept(this, data);
        }
        return null;
    }

    //des outils pour vous simplifier la vie et vous enligner dans le travail
    public enum VarType {
        Bool,
        Number
    }

    //utile surtout pour envoyé de l'informations au enfant des expressions logiques.
    private class BoolLabel {
        public String lTrue = null;
        public String lFalse = null;

        public BoolLabel(String t, String f) {
            lTrue = t;
            lFalse = f;
        }
    }


}
