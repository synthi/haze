Engine_LMGlut : CroneEngine {
  classvar num_voices = 4;

  var pg;
  var <buffers;
  var <recorders;
  var <voices;
  var <phases;
  var <levels;

  var <seek_tasks;

  *new { arg context, doneCallback;
    ^super.new(context, doneCallback);
  }

  alloc {
    buffers = Array.fill(num_voices, { arg i;
      var bufferLengthSeconds = 8;

      Buffer.alloc(
        context.server,
        context.server.sampleRate * bufferLengthSeconds,
        bufnum: i
      );
    });

    SynthDef(\recordBuf, { arg bufnum = 0, run = 0, preLevel = 1.0, recLevel = 1.0;
      var in = Mix.new(SoundIn.ar([0, 1]));

      RecordBuf.ar(
        in,
        bufnum,
        recLevel: recLevel,
        preLevel: preLevel,
        loop: 1,
        run: run
      );
    }).add;

    SynthDef(\synth, {
      arg out, phase_out, level_out, buf,
      gate=0, pos=0, speed=1, jitter=0,
      size=0.1, density=20, pitch=1, spread=0, gain=1, envscale=1,
      freeze=0, t_reset_pos=0;

      var grain_trig;
      var jitter_sig;
      var buf_dur;
      var pan_sig;
      var buf_pos;
      var pos_sig;
      var sig;
      var level;

      grain_trig = Impulse.kr(density);
      buf_dur = BufDur.kr(buf);

      pan_sig = TRand.kr(
        trig: grain_trig,
        lo: spread.neg,
        hi: spread
      );

      jitter_sig = TRand.kr(
        trig: grain_trig,
        lo: buf_dur.reciprocal.neg * jitter,
        hi: buf_dur.reciprocal * jitter
      );

      buf_pos = Phasor.kr(
        trig: t_reset_pos,
        rate: buf_dur.reciprocal / ControlRate.ir * speed,
        resetPos: pos
      );

      pos_sig = Wrap.kr(Select.kr(freeze, [buf_pos, pos]));

      sig = GrainBuf.ar(2, grain_trig, size, buf, pitch, pos_sig + jitter_sig, 2, pan_sig);

      level = EnvGen.kr(Env.asr(1, 1, 1), gate: gate, timeScale: envscale);

      Out.ar(out, sig * level * gain);
      Out.kr(phase_out, pos_sig);

      // ignore gain for level out
      Out.kr(level_out, level);
    }).add;

    context.server.sync;

    phases = Array.fill(num_voices, { arg i; Bus.control(context.server); });
    levels = Array.fill(num_voices, { arg i; Bus.control(context.server); });

    pg = ParGroup.head(context.xg);

    voices = Array.fill(num_voices, { arg i;
      Synth.new(\synth, [
        \out, context.out_b.index,
        \phase_out, phases[i].index,
        \level_out, levels[i].index,
        \buf, buffers[i],
      ], target: pg);
    });

    recorders = Array.fill(num_voices, { arg i;
      Synth.new(\recordBuf, [
        \bufnum, buffers[i].bufnum,
        \run, 0
      ], target: pg);
    });

    context.server.sync;

    this.addCommand("read", "is", { arg msg;
      var voice = msg[1] - 1;
      this.readBuf(voice, msg[2]);
    });

    this.addCommand("record", "ii", { arg msg;
      var voice = msg[1] - 1;
      recorders[voice].set(\run, msg[2]);
    });

    this.addCommand("pre_level", "if", { arg msg;
      var voice = msg[1] - 1;
      recorders[voice].set(\preLevel, msg[2]);
    });

    this.addCommand("rec_level", "if", { arg msg;
      var voice = msg[1] - 1;
      recorders[voice].set(\recLevel, msg[2]);
    });

    this.addCommand("seek", "if", { arg msg;
      var voice = msg[1] - 1;
      var lvl, pos;
      var seek_rate = 1 / 750;

      seek_tasks[voice].stop;

      // TODO: async get
      lvl = levels[voice].getSynchronous();

      if (false, { // disable seeking until fully implemented
        var step;
        var target_pos;

        // TODO: async get
        pos = phases[voice].getSynchronous();
        voices[voice].set(\freeze, 1);

        target_pos = msg[2];
        step = (target_pos - pos) * seek_rate;

        seek_tasks[voice] = Routine {
          while({ abs(target_pos - pos) > abs(step) }, {
            pos = pos + step;
            voices[voice].set(\pos, pos);
            seek_rate.wait;
          });

          voices[voice].set(\pos, target_pos);
          voices[voice].set(\freeze, 0);
          voices[voice].set(\t_reset_pos, 1);
        };

        seek_tasks[voice].play();
      }, {
        pos = msg[2];

        voices[voice].set(\pos, pos);
        voices[voice].set(\t_reset_pos, 1);
        voices[voice].set(\freeze, 0);
      });
    });

    this.addCommand("gate", "ii", { arg msg;
      var voice = msg[1] - 1;
      voices[voice].set(\gate, msg[2]);
    });

    this.addCommand("speed", "if", { arg msg;
      var voice = msg[1] - 1;
      voices[voice].set(\speed, msg[2]);
    });

    this.addCommand("jitter", "if", { arg msg;
      var voice = msg[1] - 1;
      voices[voice].set(\jitter, msg[2]);
    });

    this.addCommand("size", "if", { arg msg;
      var voice = msg[1] - 1;
      voices[voice].set(\size, msg[2]);
    });

    this.addCommand("density", "if", { arg msg;
      var voice = msg[1] - 1;
      voices[voice].set(\density, msg[2]);
    });

    this.addCommand("pitch", "if", { arg msg;
      var voice = msg[1] - 1;
      voices[voice].set(\pitch, msg[2]);
    });

    this.addCommand("spread", "if", { arg msg;
      var voice = msg[1] - 1;
      voices[voice].set(\spread, msg[2]);
    });

    this.addCommand("gain", "if", { arg msg;
      var voice = msg[1] - 1;
      voices[voice].set(\gain, msg[2]);
    });

    this.addCommand("envscale", "if", { arg msg;
      var voice = msg[1] - 1;
      voices[voice].set(\envscale, msg[2]);
    });

    this.addCommand("cutoff", "if", { arg msg;
      var voice = msg[1] - 1;
      voices[voice].set(\cutoff, msg[2]);
    });

    this.addCommand("q", "if", { arg msg;
      var voice = msg[1] - 1;
      voices[voice].set(\q, msg[2]);
    });

    num_voices.do({ arg i;
      this.addPoll(("phase_" ++ (i + 1)).asSymbol, {
        var val = phases[i].getSynchronous;
        val
      });

      this.addPoll(("level_" ++ (i + 1)).asSymbol, {
        var val = levels[i].getSynchronous;
        val
      });
    });

    seek_tasks = Array.fill(num_voices, { arg i;
      Routine {}
    });
  }

  free {
    voices.do({ arg voice; voice.free; });
    phases.do({ arg bus; bus.free; });
    levels.do({ arg bus; bus.free; });
    buffers.do({ arg b; b.free; });
    recorders.do({ arg r; r.free; });
  }
}
