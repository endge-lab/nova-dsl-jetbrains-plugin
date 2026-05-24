package dev.engine2d.nova

import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet

class NovaParserDefinition : ParserDefinition {
  override fun createLexer(project: Project?): Lexer = NovaLexer()

  override fun createParser(project: Project?): PsiParser = PsiParser { root, builder ->
    val marker = builder.mark()
    while (!builder.eof()) {
      builder.advanceLexer()
    }
    marker.done(root)
    builder.treeBuilt
  }

  override fun getFileNodeType(): IFileElementType = NovaElementTypes.FILE

  override fun getWhitespaceTokens(): TokenSet = WHITE_SPACES

  override fun getCommentTokens(): TokenSet = COMMENTS

  override fun getStringLiteralElements(): TokenSet = STRINGS

  override fun createElement(node: ASTNode): PsiElement = ASTWrapperPsiElement(node)

  override fun createFile(viewProvider: FileViewProvider): PsiFile = NovaPsiFile(viewProvider)

  override fun spaceExistenceTypeBetweenTokens(left: ASTNode?, right: ASTNode?): ParserDefinition.SpaceRequirements {
    return ParserDefinition.SpaceRequirements.MAY
  }

  companion object {
    private val WHITE_SPACES = TokenSet.create(TokenType.WHITE_SPACE)
    private val COMMENTS = TokenSet.create(NovaTokenTypes.COMMENT)
    private val STRINGS = TokenSet.create(NovaTokenTypes.STRING)
  }
}
