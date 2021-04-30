package org.nexial.core.tools.docs;

import static org.nexial.core.NexialConst.Doc.MD_EXTENSION;
import static org.nexial.core.NexialConst.TOKEN_FUNCTION_END;

final class MiniDocConst {
    static final int MIN_FUNCTION_SIZE = 3;
    static final String PARENT = "{parent}";
    static final String TITLE = "{title}";
    static final String SCRIPT = "<script>";
    static final String OPERATION_SEP = "_";
    static final String RELATIVE_LINK_REGEX = "((!?\\[[^\\]]*?\\])\\((?:(?!http).)*?\\))";
    static final String OPERATIONS_STARTED = "### Operations";
    static final String AVAILABLE_FUNCTIONS = "### Available Functions";
    static final String SEE_ALSO = "### See Also";
    static final String UI_IMAGE_PREFIX = "UI.";
    static final String H5 = "#####";
    static final String H4 = "#### ";
    static final String IMAGE = "image";
    static final String FUNCTION_SUFFIX = TOKEN_FUNCTION_END + MD_EXTENSION;
    static final String EXPRESSION_SUFFIX = "expression";
    static final String EXPRESSION_FILE1_SUFFIX = EXPRESSION_SUFFIX + MD_EXTENSION;
    static final String HREF_PREFIX = "href=\"";
    static final String HREF_LINK_PREFIX = HREF_PREFIX + "#";
    static final String IMAGE_REGEX = "src=\\\"image\\/.+?.(png|jpg)\\\"";
    static final String HTML_TEMPLATE = "/HtmlTemplate.html";
    static final String CONTENT_HTML = "content.html";
    static final String HEADER_ROW = "{header-row}";
    static final String CONFIGURATION_ROW = "{configuration-row}";
    static final String SYSVAR = "{sysvar}";
    static final String FRONT_MATTER_TEMLATE = MinifyGenerator.class.getPackage().getName().replace(".", "/") +
                                               "/MinifiedFrontMatterTemplate.md";
    public static final String TABLE_ROW_START = "<tr>\n";
    public static final String TABLE_ROW_END = "\n</tr>";
    public static final String TARGET_ATTR = "target";

    private MiniDocConst() { }

}
