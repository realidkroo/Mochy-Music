/*
 * mochy_fx.h: what a module's native audio processor exports, so mochy can run it in the mixer
 *
 * a code module (an apk signed with mochy's key, see the wiki) can bring a shared library that
 * exports one function, mochy_fx_processors. mochy lists every processor it returns in the add
 * plugin sheet under the module's name, draws its knobs from the params, and runs it on the
 * playback thread like any built-in effect.
 *
 * threads: create, destroy and the first set_params happen on mochy's worker thread; reset,
 * set_params, process, process_all and bitstream on the playback thread. each instance is only
 * ever used by one thread at a time. process must not block, lock or allocate.
 */
#ifndef MOCHY_FX_H
#define MOCHY_FX_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define MOCHY_FX_ABI 1

/* how a knob moves between its ends */
#define MOCHY_FX_LINEAR 0
#define MOCHY_FX_LOG 1
#define MOCHY_FX_INT 2
#define MOCHY_FX_CHOICE 3
#define MOCHY_FX_TOGGLE 4

/* process_all gets every channel of the song in one call, instead of process per stereo pair;
   an object decoder that needs the whole bed sets this */
#define MOCHY_FX_ALL_CHANNELS 1
/* bitstream gets each encoded frame of the song (e-ac-3 and the like), in order, before its decoded
   audio reaches process_all; frame k's audio is the k-th run of that codec's frame length after a reset */
#define MOCHY_FX_BITSTREAM 2
/* only reshapes left against right, so mochy skips it on mono lanes (centre, lfe) */
#define MOCHY_FX_STEREO_ONLY 4
/* has a tail worth drawing; mochy renders its impulse response for the decay picture */
#define MOCHY_FX_SHOWS_TAIL 8

typedef struct mochy_fx_param {
    const char* id;
    const char* name;
    const char* unit;
    float min;
    float max;
    float def;
    int scale;
    /* choice names split by '|', or null */
    const char* choices;
} mochy_fx_param;

typedef struct mochy_fx_meter {
    const char* id;
    const char* name;
    const char* unit;
    float min;
    float max;
} mochy_fx_meter;

typedef struct mochy_fx_processor {
    /* MOCHY_FX_ABI */
    int abi;
    const char* id;
    const char* name;
    const char* category;
    const char* description;
    int param_count;
    const mochy_fx_param* params;
    int meter_count;
    const mochy_fx_meter* meters;
    int flags;

    /* [channels] is the song's channel count; null when it can't run here */
    void* (*create)(double rate, int max_block, int channels);
    void (*destroy)(void* self);
    /* forget all held audio: a seek or a new song */
    void (*reset)(void* self);
    /* every knob, in params order, already within range */
    void (*set_params)(void* self, const float* values);
    /* one stereo pair in place; a mono lane comes as the same signal on both */
    void (*process)(void* self, float* left, float* right, int frames);
    /* with MOCHY_FX_ALL_CHANNELS: every channel in android's order, in place; may be null otherwise */
    void (*process_all)(void* self, float** channels, int count, int frames);
    /* with MOCHY_FX_BITSTREAM: one encoded frame and its presentation time; may be null otherwise */
    void (*bitstream)(void* self, const uint8_t* data, int size, int64_t time_us);
    /* samples the output trails the input by; may be null for none */
    int (*latency)(void* self);
    /* one value per meter; may be null */
    void (*read_meters)(void* self, float* out);
} mochy_fx_processor;

/* the one function the library exports */
typedef const mochy_fx_processor* const* (*mochy_fx_processors_fn)(int* count);
#define MOCHY_FX_ENTRY "mochy_fx_processors"

#ifdef __cplusplus
}
#endif

#endif
