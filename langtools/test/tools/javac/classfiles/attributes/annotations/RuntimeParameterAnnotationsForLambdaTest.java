/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @bug 8044411 8079060
 * @summary Tests the RuntimeParameterVisibleAnnotations/RuntimeParameterInvisibleAnnotations attribute.
 * @library /tools/lib /tools/javac/lib ../lib
 * @ignore 8079060 javac does not generate RuntimeParameterAnnotation attributes for lambda expressions
 * @build WorkAnnotations TestBase TestResult InMemoryFileManager ToolBox
 * @build TestCase ClassType TestAnnotationInfo
 * @build RuntimeParameterAnnotationsForLambdaTest AnnotationsTestBase RuntimeParameterAnnotationsTestBase
 * @run main RuntimeParameterAnnotationsForLambdaTest
 */

import java.util.List;
import java.util.stream.Collectors;

import com.sun.tools.classfile.ClassFile;
import com.sun.tools.classfile.Method;

/**
 * RuntimeParameterAnnotationsForLambdaTest is a test which checks that RuntimeVisibleParameterAnnotationsAttribute
 * and RuntimeInvisibleParameterAnnotationsAttribute are generated properly for lambda expressions.
 * The test checks both single and repeatable annotations.
 * All possible combinations of retention policies are tested.
 *
 * The test generates source code, compiles it and checks the byte code.
 *
 * See README.txt for more information.
 */
public class RuntimeParameterAnnotationsForLambdaTest extends RuntimeParameterAnnotationsTestBase {

    private static final String CLASS_NAME = "Test";
    private static final String SOURCE_TEMPLATE =
            "public class " + CLASS_NAME + " {\n" +
            "   interface I { void method(int a, double b, String c); }\n" +
            "   %SOURCE%\n" +
            "}";

    public static void main(String[] args) throws TestFailedException {
        new RuntimeParameterAnnotationsForLambdaTest().test();
    }

    @Override
    public void test() throws TestFailedException {
        try {
            for (TestAnnotationInfos annotations : getAllCombinationsOfAnnotations()) {
                try {
                    TestCase.TestMethodInfo testMethodInfo = new TestCase.TestMethodInfo(0, null, "lambda", false, false);
                    TestCase.TestParameterInfo p1 = testMethodInfo.addParameter("int", "a");
                    annotations.annotate(p1);
                    testMethodInfo.addParameter("double", "b");
                    TestCase.TestParameterInfo p3 = testMethodInfo.addParameter("String", "c");
                    annotations.annotate(p3);
                    String source = SOURCE_TEMPLATE.replace("%SOURCE%", generateLambdaSource(testMethodInfo));
                    echo("Testing:\n" + source);
                    addTestCase(source);
                    ClassFile classFile = readClassFile(compile(source).getClasses().get(CLASS_NAME));
                    boolean isFoundLambda = false;
                    for (Method method : classFile.methods) {
                        if (method.getName(classFile.constant_pool).startsWith("lambda$")) {
                            isFoundLambda = true;
                            testAttributes(testMethodInfo, classFile, method);
                        }
                    }
                    checkTrue(isFoundLambda, "The tested lambda method was not found.");
                } catch (Exception e) {
                    addFailure(e);
                }
            }
        } finally {
            checkStatus();
        }
    }

    public String generateLambdaSource(TestCase.TestMethodInfo method) {
        return method.parameters.stream()
                .map(TestCase.TestParameterInfo::generateSource)
                .collect(Collectors.joining(", ", "I i = (", ") -> {};"));
    }

    @Override
    public List<TestCase> generateTestCases() {
        throw new UnsupportedOperationException();
    }
}
