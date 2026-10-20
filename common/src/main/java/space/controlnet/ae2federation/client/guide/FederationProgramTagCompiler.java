package space.controlnet.ae2federation.client.guide;

import guideme.compiler.IdUtils;
import guideme.compiler.IndexingContext;
import guideme.compiler.IndexingSink;
import guideme.compiler.PageCompiler;
import guideme.compiler.tags.BlockTagCompiler;
import guideme.document.block.LytBlockContainer;
import guideme.libs.mdast.mdx.model.MdxJsxElementFields;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * {@code <FederationProgram>}: a program file from the guide's assets, shown as its script or as one function's graph.
 *
 * <pre>{@code
 * <FederationProgram src="../assets/programs/low_iron_alarm.ae2f" function="low_iron_alarm" />
 * }</pre>
 *
 * {@code src} is relative to the page, as an image's is; {@code function} picks the graph, the file's first function
 * when left out. A program that does not check shows as an error on the page.
 */
public final class FederationProgramTagCompiler extends BlockTagCompiler {
    public static final String TAG_NAME = "FederationProgram";

    @Override
    public Set<String> getTagNames() {
        return Set.of(TAG_NAME);
    }

    @Override
    protected void compile(PageCompiler compiler, LytBlockContainer parent, MdxJsxElementFields el) {
        var src = el.getAttributeString("src", "");
        if (src.isEmpty()) {
            parent.appendError(compiler, "FederationProgram needs src", el);
            return;
        }
        byte[] bytes;
        try {
            bytes = compiler.loadAsset(IdUtils.resolveLink(src, compiler.getPageId()));
        } catch (RuntimeException e) {
            parent.appendError(compiler, "Bad program path " + src + ": " + e.getMessage(), el);
            return;
        }
        if (bytes == null) {
            parent.appendError(compiler, "Missing program " + src, el);
            return;
        }
        GuideProgram program;
        try {
            program = GuideProgram.of(new String(bytes, StandardCharsets.UTF_8), el.getAttributeString("function", ""),
                    new GuideCatalog());
        } catch (IllegalArgumentException e) {
            parent.appendError(compiler, src + ": " + e.getMessage(), el);
            return;
        }
        var block = new LytFederationProgram(program);
        block.setFullWidth(true);
        block.setMarginTop(5);
        block.setMarginBottom(5);
        parent.append(block);
    }

    /** The code is not text to search. */
    @Override
    public void index(IndexingContext indexer, MdxJsxElementFields el, IndexingSink sink) {
    }
}
