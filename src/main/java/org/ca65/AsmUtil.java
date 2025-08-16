package org.ca65;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import org.ca65.psi.*;
import org.ca65.psi.AsmDotexpr;
import org.ca65.psi.AsmExpr;
import org.ca65.psi.AsmMarker;
import org.ca65.psi.AsmTypes;
import org.ca65.psi.impl.AsmPsiImplUtil;

import java.util.*;

import com.intellij.openapi.diagnostic.Logger;

public class AsmUtil {

    private static final Logger LOG = Logger.getInstance(AsmUtil.class);

    public static PsiNamedElement findDefinition(AsmFile asmFile, String identifier) {
        if(asmFile == null) {
            return null;
        }
        Set<String> visited = new HashSet<>();
        return findDefinitionRecursive(asmFile, identifier, visited);
    }

    private static PsiNamedElement findDefinitionRecursive(AsmFile asmFile, String identifier, Set<String> visited) {
        VirtualFile vf = asmFile.getVirtualFile();
        if (vf != null) {
            String key = vf.getPath();
            if (!visited.add(key)) {
                // already visited
                return null;
            }
        }
        // Defined labels
        AsmMarker[] markers = PsiTreeUtil.getChildrenOfType(asmFile, AsmMarker.class);
        if (markers != null) {
            for (AsmMarker marker : markers) {
                if (identifier.equals(AsmPsiImplUtil.getLabelName(marker))) {
                    return marker;
                }
            }
        }
        // Imported values. For reasons unknown, getChildrenOfType(asmFile, AsmIdentifierdef.class) is always null, but this works.
        AsmImports[] importStatements = PsiTreeUtil.getChildrenOfType(asmFile, AsmImports.class);
        if (importStatements != null) {
            for (AsmImports importStatement : importStatements) {
                for(AsmIdentifierdef identifierdef : importStatement.getIdentifierdefList()) {
                    if (identifier.equals(AsmPsiImplUtil.getLabelName(identifierdef))) {
                        return identifierdef;
                    }
                }
            }
        }
        // Numeric constants
        AsmDefineConstantNumeric[] numericConstantList = PsiTreeUtil.getChildrenOfType(asmFile, AsmDefineConstantNumeric.class);
        if (numericConstantList != null) {
            for (AsmDefineConstantNumeric numericConstant : numericConstantList) {
                AsmIdentifierdef identifierDef = numericConstant.getIdentifierdef();
                if (identifier.equals(AsmPsiImplUtil.getLabelName(identifierDef))) {
                    return identifierDef;
                }
            }
        }
        // label constants
        AsmDefineConstantLabel[] labelConstantList = PsiTreeUtil.getChildrenOfType(asmFile, AsmDefineConstantLabel.class);
        if (labelConstantList != null) {
            for (AsmDefineConstantLabel labelConstant : labelConstantList) {
                AsmIdentifierdef identifierDef = labelConstant.getIdentifierdef();
                if (identifier.equals(AsmPsiImplUtil.getLabelName(identifierDef))) {
                    return identifierDef;
                }
            }
        }
        // Not found in this file, try includes
        for (AsmFile included : getIncludedFiles(asmFile)) {
            PsiNamedElement fromIncluded = findDefinitionRecursive(included, identifier, visited);
            if (fromIncluded != null) {
                return fromIncluded;
            }
        }
        return null;
    }

    static List<AsmFile> getIncludedFiles(AsmFile asmFile) {
        List<AsmFile> result = new ArrayList<>();
        Project project = asmFile.getProject();
        VirtualFile baseDir = null;
        PsiFile containingFile = asmFile.getContainingFile();
        if (containingFile.getVirtualFile() != null) {
            baseDir = containingFile.getVirtualFile().getParent();
        } else if (asmFile.getVirtualFile() != null) {
            baseDir = asmFile.getVirtualFile().getParent();
        }
        AsmDotexpr[] dotexprs = PsiTreeUtil.getChildrenOfType(asmFile, AsmDotexpr.class);
        assert baseDir != null;
        if (dotexprs == null) return result;

        for (AsmDotexpr dot : dotexprs) {

            PsiElement first = dot.getFirstChild();
            if (first == null) continue;
            String kw = first.getText();
            if (kw == null) continue;
            if (!kw.equalsIgnoreCase(".include")) continue;
            String path = null;

            AsmExpr expr = PsiTreeUtil.findChildOfType(dot, AsmExpr.class);

            if (expr != null) {

                String raw = expr.getText();

                if (raw != null && raw.length() >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) {
                    path = raw.substring(1, raw.length() - 1);
                } else {
                    path = raw;
                }
                LOG.info("getIncludedFiles - FOUND PATH: " + path);
            }

            if (path == null || path.isEmpty()) continue;
            VirtualFile includedVf = VfsUtilCore.findRelativeFile(path, baseDir);
            if (includedVf != null) {
                PsiFile psiFile = PsiManager.getInstance(project).findFile(includedVf);
                if (psiFile instanceof AsmFile) {
                    result.add((AsmFile) psiFile);
                }
            }
        }
        return result;
    }

    public static List<AsmMarker> findLabels(Project project, String label) {
        List<AsmMarker> result = new ArrayList<>();
        Collection<VirtualFile> virtualFiles =
                FileTypeIndex.getFiles(AsmFileType.INSTANCE, GlobalSearchScope.allScope(project));
        for (VirtualFile virtualFile : virtualFiles) {
            AsmFile asmFile = (AsmFile) PsiManager.getInstance(project).findFile(virtualFile);
            if (asmFile != null) {
                AsmMarker[] markers = PsiTreeUtil.getChildrenOfType(asmFile, AsmMarker.class);
                if (markers != null) {
                    for (AsmMarker marker : markers) {
                        if (label.equals(AsmPsiImplUtil.getLabelName(marker))) {
                            result.add(marker);
                        }
                    }
                }
            }
        }
        return result;
    }

    public static List<AsmMarker> findLabels(Project project) {
        List<AsmMarker> result = new ArrayList<>();
        Collection<VirtualFile> virtualFiles =
                FileTypeIndex.getFiles(AsmFileType.INSTANCE, GlobalSearchScope.allScope(project));
        for (VirtualFile virtualFile : virtualFiles) {
            AsmFile asmFile = (AsmFile) PsiManager.getInstance(project).findFile(virtualFile);
            if (asmFile != null) {
                AsmMarker[] markers = PsiTreeUtil.getChildrenOfType(asmFile, AsmMarker.class);
                if (markers != null) {
                    Collections.addAll(result, markers);
                }
            }
        }
        return result;
    }
}
