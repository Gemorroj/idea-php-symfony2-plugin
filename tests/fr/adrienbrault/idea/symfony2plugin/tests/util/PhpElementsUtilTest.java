package fr.adrienbrault.idea.symfony2plugin.tests.util;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.php.lang.PhpFileType;
import com.jetbrains.php.lang.psi.PhpPsiElementFactory;
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression;
import com.jetbrains.php.lang.psi.elements.Variable;
import fr.adrienbrault.idea.symfony2plugin.tests.SymfonyLightCodeInsightFixtureTestCase;
import fr.adrienbrault.idea.symfony2plugin.util.PhpElementsUtil;

import java.io.File;

/**
 * @author Daniel Espendiller <daniel@espendiller.net>
 */
public class PhpElementsUtilTest extends SymfonyLightCodeInsightFixtureTestCase {

    public void setUp() throws Exception {
        super.setUp();
        myFixture.copyFileToProject("PhpElementsUtil.php");
    }

    public String getTestDataPath() {
        return new File(this.getClass().getResource("fixtures").getFile()).getAbsolutePath();
    }

    /**
     * @see fr.adrienbrault.idea.symfony2plugin.util.PhpElementsUtil#getMethodParameterTypeHint
     */
    public void testGetMethodParameterClassHint() {
        assertEquals("\\DateTime", PhpElementsUtil.getMethodParameterTypeHint(
            PhpPsiElementFactory.createMethod(getProject(), "function foo(\\DateTime $e) {}")
        ));

        assertEquals("\\Iterator", PhpElementsUtil.getMethodParameterTypeHint(
            PhpPsiElementFactory.createMethod(getProject(), "function foo(/* foo */ \\Iterator $a, \\DateTime $b")
        ));

        assertNull(PhpElementsUtil.getMethodParameterTypeHint(
            PhpPsiElementFactory.createMethod(getProject(), "function foo(/* foo */ $a, \\DateTime $b")
        ));
    }

    /**
     * @see fr.adrienbrault.idea.symfony2plugin.util.PhpElementsUtil#getArrayKeyValueMap
     */
    public void testGetArrayKeyValueMap() {
        assertEquals("foo", PhpElementsUtil.getArrayKeyValueMap(PhpPsiElementFactory.createPhpPsiFromText(getProject(), ArrayCreationExpression.class, "$foo = ['foo' => 'foo'];")).get("foo"));
        assertEquals("foo", PhpElementsUtil.getArrayKeyValueMap(PhpPsiElementFactory.createPhpPsiFromText(getProject(), ArrayCreationExpression.class, "$foo = [1 => 'foo'];")).get("1"));

        assertSize(0, PhpElementsUtil.getArrayKeyValueMap(PhpPsiElementFactory.createPhpPsiFromText(getProject(), ArrayCreationExpression.class, "$foo = [null => 'foo'];")).keySet());
        assertSize(0, PhpElementsUtil.getArrayKeyValueMap(PhpPsiElementFactory.createPhpPsiFromText(getProject(), ArrayCreationExpression.class, "$foo = ['' => 'foo'];")).keySet());
    }

    /**
     * @see fr.adrienbrault.idea.symfony2plugin.util.PhpElementsUtil#getFirstVariableTypeInScope(Variable)
     */
    public void testGetFirstVariableTypeInScope() {
        PsiElement psiElement = myFixture.configureByText(PhpFileType.INSTANCE, "<?php" +
            "$foo = new \\DateTime();\n" +
            "$d->dispatch('foo', $f<caret>oo)->;").findElementAt(myFixture.getCaretOffset()).getParent();

        assertEquals("\\DateTime", PhpElementsUtil.getFirstVariableTypeInScope((Variable) psiElement));

        psiElement = myFixture.configureByText(PhpFileType.INSTANCE, "<?php" +
            "function foo() {" +
            "  $foo = new \\DateTime();\n" +
            "  $d->dispatch('foo', $f<caret>oo);\n" +
            "}").findElementAt(myFixture.getCaretOffset()).getParent();

        assertEquals("\\DateTime", PhpElementsUtil.getFirstVariableTypeInScope((Variable) psiElement));
    }

    /**
     * @see fr.adrienbrault.idea.symfony2plugin.util.PhpElementsUtil#getFirstVariableTypeInScope(Variable)
     */
    public void testGetFirstVariableTypeInScopeNotFound() {
        PsiElement psiElement = myFixture.configureByText(PhpFileType.INSTANCE, "<?php" +
            "$foo = new \\DateTime();\n" +
            "function foo() {\n" +
            "  $d->dispatch('foo', $f<caret>oo);" +
            "}").findElementAt(myFixture.getCaretOffset()).getParent();

        assertNull(PhpElementsUtil.getFirstVariableTypeInScope((Variable) psiElement));
    }

    /**
     * @see fr.adrienbrault.idea.symfony2plugin.util.PhpElementsUtil#getClassInsideNamespaceScope
     */
    public void testGetClassInsideNamespaceScope() {
        assertNotNull(PhpElementsUtil.getClassInsideNamespaceScope(getProject(), "Foo\\Foo", "\\Foo\\Bar"));
        assertNotNull(PhpElementsUtil.getClassInsideNamespaceScope(getProject(), "Foo\\Foo", "Bar"));
        assertNotNull(PhpElementsUtil.getClassInsideNamespaceScope(getProject(), "\\Foo\\Foo", "Bar"));
        assertNotNull(PhpElementsUtil.getClassInsideNamespaceScope(getProject(), "\\Foo\\Foo", "Bar\\"));
        assertNotNull(PhpElementsUtil.getClassInsideNamespaceScope(getProject(), "\\Foo\\Foo\\", "Bar\\"));

        assertNull(PhpElementsUtil.getClassInsideNamespaceScope(getProject(), "Fooa", "Bar"));
        assertNull(PhpElementsUtil.getClassInsideNamespaceScope(getProject(), "Fooa\\Foo", "Bar"));
    }
}
