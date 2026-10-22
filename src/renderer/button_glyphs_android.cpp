#include "button_glyphs.h"

namespace renderer {

struct ButtonGlyphs::Impl {};

ButtonGlyphs::ButtonGlyphs(Diligent::IRenderDevice*) : impl_(nullptr) {}
ButtonGlyphs::~ButtonGlyphs() = default;
void ButtonGlyphs::SetStyle(GlyphStyle) {}
void ButtonGlyphs::SetBindings(const KeyBindings&) {}
Diligent::ITexture* ButtonGlyphs::Replace(
    uint64_t, const TextureCache::LoadedTexture*) {
  return nullptr;
}

}  // namespace renderer
