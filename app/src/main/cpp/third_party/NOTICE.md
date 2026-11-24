# Third-party code in the native libraries

## jamesdsp_eel2 (Liveprog)

The EEL2 script engine with JamesDSP's extensions, taken from the libjamesdsp subtree of
[JDSP4Linux](https://github.com/Audio4Linux/JDSP4Linux) (`libjamesdsp/subtree/Main/libjamesdsp/jni/jamesdsp/jdsp/Effects/eel2`).

- EEL2 core (`nseel-compiler.c`, `nseel-ram.c`, `fft.c`, …): Cockos Incorporated and Nullsoft,
  zlib-style license, kept in each file's header.
- JamesDSP extensions and `numericSys`: James Fung (james34602), GPL-2.0-or-later, see
  `jamesdsp_eel2/LICENSE-libjamesdsp-GPL2`. Some numericSys parts carry their own permissive or LGPL
  terms in their headers (libsamplerate BSD-2, HPA LGPL-2.1+, SolvOpt, Mersenne Twister).
- Packaged for Linux by Tim Schneeberger (ThePBone) in JDSP4Linux and RootlessJamesDSP, GPL-3.0.

Changed for Mochy, as the zlib license asks to be marked: `nseel-compiler.c` leaves out the builtins
that touch files, folders, threads or sleep when `MOCHY_EEL_SANDBOX` is defined (see its header).
Nothing else was changed; `dirent.h` and `dr_mp3.h` were left out because nothing builds them.

## fx/reverb/progenitor (Progenitor 2)

From [sndfilter](https://github.com/voidqk/sndfilter) by Sean Connelly, MIT License, with James Fung's
changes from JamesDSP. Ported to C++ for Mochy; the full MIT notice is in `fx/reverb/progenitor.h`.

## Not here on purpose

Mochy has no Cavern code. Cavern's license adds terms (no selling, no ads) that GPL-3 code owned by
other people can't take on, so an Atmos object decoder belongs in a module of its own, which plugs in
through `modules/api/native/mochy_fx.h` (docs/notes/mixer.md).
