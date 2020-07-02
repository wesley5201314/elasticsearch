/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.painless.node;

import org.elasticsearch.painless.FunctionRef;
import org.elasticsearch.painless.Location;
import org.elasticsearch.painless.ir.ClassNode;
import org.elasticsearch.painless.ir.DefInterfaceReferenceNode;
import org.elasticsearch.painless.ir.TypedCaptureReferenceNode;
import org.elasticsearch.painless.ir.TypedInterfaceReferenceNode;
import org.elasticsearch.painless.lookup.def;
import org.elasticsearch.painless.symbol.Decorations.Read;
import org.elasticsearch.painless.symbol.Decorations.TargetType;
import org.elasticsearch.painless.symbol.Decorations.ValueType;
import org.elasticsearch.painless.symbol.Decorations.Write;
import org.elasticsearch.painless.symbol.ScriptScope;
import org.elasticsearch.painless.symbol.SemanticScope;

import java.util.Objects;

/**
 * Represents a function reference.
 */
public class EFunctionRef extends AExpression {

    private final String symbol;
    private final String methodName;

    public EFunctionRef(int identifier, Location location, String symbol, String methodName) {
        super(identifier, location);

        this.symbol = Objects.requireNonNull(symbol);
        this.methodName = Objects.requireNonNull(methodName);
    }

    public String getSymbol() {
        return symbol;
    }

    public String getCall() {
        return methodName;
    }

    @Override
    Output analyze(ClassNode classNode, SemanticScope semanticScope) {
        ScriptScope scriptScope = semanticScope.getScriptScope();
        boolean read = semanticScope.getCondition(this, Read.class);
        TargetType targetType = semanticScope.getDecoration(this, TargetType.class);

        Output output = new Output();
        Class<?> valueType;
        Class<?> type = scriptScope.getPainlessLookup().canonicalTypeNameToType(symbol);

        if (symbol.equals("this") || type != null)  {
            if (semanticScope.getCondition(this, Write.class)) {
                throw createError(new IllegalArgumentException(
                        "invalid assignment: cannot assign a value to function reference [" + symbol + ":" + methodName + "]"));
            }

            if (read == false) {
                throw createError(new IllegalArgumentException(
                        "not a statement: function reference [" + symbol + ":" + methodName + "] not used"));
            }

            if (targetType == null) {
                valueType = String.class;
                String defReferenceEncoding = "S" + symbol + "." + methodName + ",0";

                DefInterfaceReferenceNode defInterfaceReferenceNode = new DefInterfaceReferenceNode();

                defInterfaceReferenceNode.setLocation(getLocation());
                defInterfaceReferenceNode.setExpressionType(valueType);
                defInterfaceReferenceNode.setDefReferenceEncoding(defReferenceEncoding);

                output.expressionNode = defInterfaceReferenceNode;
            } else {
                FunctionRef ref = FunctionRef.create(scriptScope.getPainlessLookup(), scriptScope.getFunctionTable(),
                        getLocation(), targetType.getTargetType(), symbol, methodName, 0);
                valueType = targetType.getTargetType();

                TypedInterfaceReferenceNode typedInterfaceReferenceNode = new TypedInterfaceReferenceNode();
                typedInterfaceReferenceNode.setLocation(getLocation());
                typedInterfaceReferenceNode.setExpressionType(valueType);
                typedInterfaceReferenceNode.setReference(ref);
                output.expressionNode = typedInterfaceReferenceNode;
            }
        } else {
            if (semanticScope.getCondition(this, Write.class)) {
                throw createError(new IllegalArgumentException(
                        "invalid assignment: cannot assign a value to capturing function reference [" + symbol + ":"  + methodName + "]"));
            }

            if (read == false) {
                throw createError(new IllegalArgumentException(
                        "not a statement: capturing function reference [" + symbol + ":"  + methodName + "] not used"));
            }

            SemanticScope.Variable captured = semanticScope.getVariable(getLocation(), symbol);
            if (targetType == null) {
                String defReferenceEncoding;
                if (captured.getType() == def.class) {
                    // dynamic implementation
                    defReferenceEncoding = "D" + symbol + "." + methodName + ",1";
                } else {
                    // typed implementation
                    defReferenceEncoding = "S" + captured.getCanonicalTypeName() + "." + methodName + ",1";
                }
                valueType = String.class;

                DefInterfaceReferenceNode defInterfaceReferenceNode = new DefInterfaceReferenceNode();

                defInterfaceReferenceNode.setLocation(getLocation());
                defInterfaceReferenceNode.setExpressionType(valueType);
                defInterfaceReferenceNode.addCapture(captured.getName());
                defInterfaceReferenceNode.setDefReferenceEncoding(defReferenceEncoding);
                output.expressionNode = defInterfaceReferenceNode;
            } else {
                valueType = targetType.getTargetType();
                // static case
                if (captured.getType() != def.class) {
                    FunctionRef ref = FunctionRef.create(scriptScope.getPainlessLookup(), scriptScope.getFunctionTable(), getLocation(),
                            targetType.getTargetType(), captured.getCanonicalTypeName(), methodName, 1);

                    TypedInterfaceReferenceNode typedInterfaceReferenceNode = new TypedInterfaceReferenceNode();
                    typedInterfaceReferenceNode.setLocation(getLocation());
                    typedInterfaceReferenceNode.setExpressionType(valueType);
                    typedInterfaceReferenceNode.addCapture(captured.getName());
                    typedInterfaceReferenceNode.setReference(ref);
                    output.expressionNode = typedInterfaceReferenceNode;
                } else {
                    TypedCaptureReferenceNode typedCaptureReferenceNode = new TypedCaptureReferenceNode();
                    typedCaptureReferenceNode.setLocation(getLocation());
                    typedCaptureReferenceNode.setExpressionType(valueType);
                    typedCaptureReferenceNode.addCapture(captured.getName());
                    typedCaptureReferenceNode.setMethodName(methodName);
                    output.expressionNode = typedCaptureReferenceNode;
                }
            }
        }

        semanticScope.putDecoration(this, new ValueType(valueType));

        return output;
    }
}
