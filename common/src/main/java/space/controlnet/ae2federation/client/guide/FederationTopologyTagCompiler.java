package space.controlnet.ae2federation.client.guide;

import guideme.compiler.IndexingContext;
import guideme.compiler.IndexingSink;
import guideme.compiler.PageCompiler;
import guideme.compiler.tags.BlockTagCompiler;
import guideme.document.block.LytBlockContainer;
import guideme.libs.mdast.mdx.model.MdxJsxElementFields;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/**
 * {@code <FederationTopology>}: a topology diagram of an example build, written as child tags.
 *
 * <pre>{@code
 * <FederationTopology>
 *   <Network key="a" label="Network A" color="#915dcd" column="0" row="0" details="Crafting CPU|Terminal" />
 *   <Network key="b" label="Network B" color="#5CA7CD" column="1" row="0" />
 *   <Rule user="a" source="b" capability="crafting" />
 *   <Energy first="a" second="b" />
 * </FederationTopology>
 * }</pre>
 *
 * A rule's {@code state} is {@code active} (the default), {@code reexport}, {@code waiting} or {@code error}. The
 * network keys are not item ids, hence {@code key} rather than {@code id}.
 */
public final class FederationTopologyTagCompiler extends BlockTagCompiler {
    public static final String TAG_NAME = "FederationTopology";
    private static final List<String> ATTRIBUTES = List.of("key", "label", "color", "column", "row", "details", "user",
            "source", "capability", "state", "first", "second");

    @Override
    public Set<String> getTagNames() {
        return Set.of(TAG_NAME);
    }

    @Override
    protected void compile(PageCompiler compiler, LytBlockContainer parent, MdxJsxElementFields el) {
        var elements = new ArrayList<TopologyDiagram.Element>();
        for (var child : el.children()) {
            if (!(child instanceof MdxJsxElementFields tag)) continue;
            var attributes = new LinkedHashMap<String, String>();
            for (var name : ATTRIBUTES) {
                if (tag.hasAttribute(name)) attributes.put(name, tag.getAttributeString(name, ""));
            }
            elements.add(new TopologyDiagram.Element(tag.name(), attributes));
        }
        var parsed = TopologyDiagram.parse(elements);
        if (!parsed.problems().isEmpty()) {
            parent.appendError(compiler, String.join("; ", parsed.problems()), el);
            return;
        }
        var block = new LytFederationTopology(parsed.diagram());
        block.setFullWidth(true);
        block.setMarginTop(5);
        block.setMarginBottom(5);
        parent.append(block);
    }

    /** The diagram's labels are drawn, not text to search. */
    @Override
    public void index(IndexingContext indexer, MdxJsxElementFields el, IndexingSink sink) {
    }
}
