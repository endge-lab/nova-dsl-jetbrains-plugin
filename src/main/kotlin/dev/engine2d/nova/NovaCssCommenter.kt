package dev.engine2d.nova

import com.intellij.lang.Commenter

class NovaCssCommenter : Commenter {
  override fun getLineCommentPrefix(): String? = null

  override fun getBlockCommentPrefix(): String = "/*"

  override fun getBlockCommentSuffix(): String = "*/"

  override fun getCommentedBlockCommentPrefix(): String? = null

  override fun getCommentedBlockCommentSuffix(): String? = null
}
