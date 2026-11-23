#include "plugin.h"
#include <cstdio>
using namespace fx;
static void js(const std::string& s) { putchar('"'); for (char c : s) { if (c == '"' || c == '\\') putchar('\\'); putchar(c); } putchar('"'); }
int main() {
    printf("[");
    bool first = true;
    for (auto& t : builtinTypes()) {
        if (!first) printf(","); first = false;
        printf("{\"id\":"); js(t.info.id); printf(",\"name\":"); js(t.info.name); printf(",\"category\":"); js(t.info.category); printf(",\"description\":"); js(t.info.description);
        printf(",\"params\":[");
        for (size_t i = 0; i < t.info.params.size(); i++) { auto& p = t.info.params[i]; if (i) printf(","); printf("{\"id\":"); js(p.id); printf(",\"name\":"); js(p.name); printf(",\"choices\":"); js(p.choices); printf("}"); }
        printf("],\"meters\":[");
        for (size_t i = 0; i < t.info.meters.size(); i++) { if (i) printf(","); printf("{\"id\":"); js(t.info.meters[i].id); printf(",\"name\":"); js(t.info.meters[i].name); printf("}"); }
        printf("],\"presets\":[");
        for (size_t i = 0; i < t.info.presets.size(); i++) { if (i) printf(","); js(t.info.presets[i].name); }
        printf("]}");
    }
    printf("]\n");
}
