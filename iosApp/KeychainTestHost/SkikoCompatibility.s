// Compose 1.8.1's exported static framework references four legacy Skiko bridge
// names while the bundled 0.9.4.2 archive exports their package-qualified names.
// These tail-call aliases are needed only by this minimal standalone test host.
.text
.globl _org_jetbrains_skia_TextBlobBuilderRunHandler__1nGetFinalizer
_org_jetbrains_skia_TextBlobBuilderRunHandler__1nGetFinalizer:
    b _org_jetbrains_skia_shaper_TextBlobBuilderRunHandler__1nGetFinalizer

.globl _org_jetbrains_skia_TextBlobBuilderRunHandler__1nMake
_org_jetbrains_skia_TextBlobBuilderRunHandler__1nMake:
    b _org_jetbrains_skia_shaper_TextBlobBuilderRunHandler__1nMake

.globl _org_jetbrains_skia_TextBlobBuilderRunHandler__1nMakeBlob
_org_jetbrains_skia_TextBlobBuilderRunHandler__1nMakeBlob:
    b _org_jetbrains_skia_shaper_TextBlobBuilderRunHandler__1nMakeBlob

.globl _org_jetbrains_skia_svg_SVGCanvasKt__1nMake
_org_jetbrains_skia_svg_SVGCanvasKt__1nMake:
    b _org_jetbrains_skia_svg_SVGCanvas__1nMake
