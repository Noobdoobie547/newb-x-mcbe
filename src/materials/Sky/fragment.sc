#ifndef INSTANCING
  $input v_worldPos, v_underwaterRainTimeDay
#endif

#include <bgfx_shader.sh>

#ifndef INSTANCING
  #include <newb/main.sh>
  uniform vec4 TimeOfDay;
  uniform vec4 FogColor;
  uniform vec4 FogAndDistanceControl;
#endif

#ifndef INSTANCING
  #ifdef NL_SKY_CLOUDS

  float bsbeHash(float h) {
    return fract(sin(h) * 43758.5453);
  }

  #ifdef NL_SKY_CLOUDS_NOISE_TEX
    SAMPLER2D_AUTOREG(s_NoiseTexture);

    float bsbeVoronoi(vec2 pos) {
      return texture2DLod(s_NoiseTexture, pos * (1.0 / NL_SKY_CLOUDS_NOISE_CELLS), 0.0).r;
    }
  #else
    float bsbeVoronoi(vec2 pos) {
      vec2 p = floor(pos);
      vec2 f = fract(pos);
      float dist = 1.0;
      for (float y = -1.0; y <= 1.0; y++) {
        for (float x = -1.0; x <= 1.0; x++) {
          vec2 ne = vec2(x, y);
          vec2 pn = p + ne;
          float n = pn.x + pn.y * 57.0;
          dist = min(dist, length(ne + bsbeHash(n) - f));
        }
      }
      return dist;
    }
  #endif

  float bsbeFbm(vec2 pos, float pDens, float fTime, int nOct) {
    float sum = 0.0;
    float sDens = 1.0;
    pos += fTime * 0.001 * NL_SKY_CLOUDS_SPEED;
    for (int i = 0; i < 4; i++) {
      if (i < nOct) sum += bsbeVoronoi(pos) * sDens * pDens;
      else          sum += 0.42 * sDens * pDens;
      sDens *= 0.5;
      pos *= 2.5;
      pos += fTime * 0.05 * NL_SKY_CLOUDS_SPEED;
    }
    return saturate(1.0 - sum);
  }

  vec3 bsbeSatur(vec3 c, float sat) {
    float lum = dot(c, vec3(0.2125, 0.7154, 0.0721));
    return mix(vec3_splat(lum), c, sat);
  }

  vec3 bsbeClouds(vec3 color, vec3 nPos, float fTime, vec3 horC, vec3 zenC, vec2 fragCoord) {
    vec2 cloudP = (nPos.xz / nPos.y) * NL_SKY_CLOUDS_SCALE;
    cloudP -= cloudP * bsbeHash(fragCoord.x + fragCoord.y * 57.0) * 0.05;

    float sDens = NL_SKY_CLOUDS_DENSITY;
    vec3 cloDirC = horC * NL_SKY_CLOUDS_BRIGHTNESS;
    vec3 cloAmbC = bsbeSatur(zenC, 0.7);

    float lodY = nPos.y / NL_SKY_CLOUDS_LOD_Y;
    int nOct = int(step(1.0, lodY) + step(1.58, lodY) + step(2.5, lodY) + step(3.95, lodY));
    if (nOct > 0) {
      for (int i = 0; i < NL_SKY_CLOUDS_LAYERS; i++) {
        float cloudM = bsbeFbm(cloudP, sDens, fTime, nOct);
        cloDirC = mix(cloDirC, cloAmbC, NL_SKY_CLOUDS_AMBIENT_MIX);
        color = mix(color, cloDirC, cloudM * smoothstep(0.0, NL_SKY_CLOUDS_HORIZON_FADE, nPos.y));
        sDens += (i <= 6) ? -0.1 : 0.1;
        cloudP -= cloudP * NL_SKY_CLOUDS_LAYER_SQUEEZE;
      }
    }
    return color;
  }

  #endif
#endif

void main() {
  #ifndef INSTANCING
    vec3 viewDir = normalize(v_worldPos);

    nl_environment env;
    env.end = false;
    env.nether = false;
    env.underwater = v_underwaterRainTimeDay.x > 0.5;
    env.rainFactor = v_underwaterRainTimeDay.y;
    env.dayFactor = v_underwaterRainTimeDay.w;
    env.fogCol = FogColor.rgb;
    env = calculateSunParams(env, TimeOfDay.x);

    nl_skycolor skycol = nlOverworldSkyColors(env);

    vec3 skyColor = nlRenderSky(skycol, env, -viewDir, v_underwaterRainTimeDay.z, true);
    #ifdef NL_SHOOTING_STAR
      skyColor += NL_SHOOTING_STAR*nlRenderShootingStar(viewDir, env.fogCol, v_underwaterRainTimeDay.z);
    #endif
    #ifdef NL_GALAXY_STARS
      skyColor += NL_GALAXY_STARS*nlRenderGalaxy(viewDir, env.fogCol, env, v_underwaterRainTimeDay.z);
    #endif

    #ifdef NL_SKY_CLOUDS
      if (!env.underwater && env.rainFactor < 0.99) {
        vec3 withClouds = bsbeClouds(skyColor, viewDir, v_underwaterRainTimeDay.z,
                                     skycol.horizon, skycol.zenith, gl_FragCoord.xy);
        skyColor = mix(withClouds, skyColor, env.rainFactor);
      }
    #endif

    skyColor = colorCorrection(skyColor);

    gl_FragColor = vec4(skyColor, 1.0);
  #else
    gl_FragColor = vec4(0.0, 0.0, 0.0, 0.0);
  #endif
}
