/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

(function () {
  "use strict";

  const EXTENSION_PORT = "ClarusTheater";
  let activePort = null;
  let lastTrackedVideo = null;

  function getOrCreatePort() {
    if (!activePort || activePort.error) {
      activePort = null;
      try {
        if (typeof browser === "undefined" || !browser.runtime || typeof browser.runtime.connectNative !== "function") {
          return null;
        }
        activePort = browser.runtime.connectNative(EXTENSION_PORT);
        if (activePort && activePort.error) {
          activePort = null;
          return null;
        }
        if (activePort && activePort.onMessage) {
          activePort.onMessage.addListener(handleNativeMessage);
        }
        if (activePort && activePort.onDisconnect) {
          activePort.onDisconnect.addListener(() => {
            activePort = null;
          });
        }
      } catch (e) {
        activePort = null;
        console.debug("[ClarusTheater] Port connection error:", e);
      }
    }
    return activePort;
  }

  function handleNativeMessage(msg) {
    if (!msg || typeof msg !== "object") return;

    if (msg.action === "SEEK_AND_SYNC") {
      const video = lastTrackedVideo;
      if (video) {
        if (typeof msg.currentTime === "number" && Number.isFinite(msg.currentTime)) {
          try {
            video.currentTime = msg.currentTime;
          } catch (e) {}
        }
        if (msg.paused === true && !video.paused) {
          try {
            video.pause();
          } catch (e) {}
        } else if (msg.paused === false && video.paused) {
          video.play().catch(() => {});
        }
      }
    } else if (msg.action === "PAUSE_WEB_VIDEO") {
      const video = lastTrackedVideo;
      if (video && !video.paused) {
        try {
          video.pause();
        } catch (e) {}
      }
    } else if (msg.action === "EXIT_FULLSCREEN") {
      if (document.fullscreenElement || document.webkitFullscreenElement || document.mozFullScreenElement) {
        if (document.exitFullscreen) {
          document.exitFullscreen().catch(() => {});
        } else if (document.webkitExitFullscreen) {
          document.webkitExitFullscreen();
        }
      }
    }
  }

  function resolveAbsoluteUrl(url) {
    if (!url) return "";
    try {
      return new URL(url, document.baseURI).href;
    } catch (e) {
      return url;
    }
  }

  function extractVideoSource(video) {
    if (!video) return "";
    if (video.currentSrc) return resolveAbsoluteUrl(video.currentSrc);
    if (video.src) return resolveAbsoluteUrl(video.src);

    const sources = video.querySelectorAll("source");
    for (let i = 0; i < sources.length; i++) {
      const src = sources[i].getAttribute("src");
      if (src) return resolveAbsoluteUrl(src);
    }
    return "";
  }

  function cleanTitle(rawTitle) {
    if (!rawTitle) return "";
    return rawTitle
      .replace(/\s*-\s*YouTube\s*$/i, "")
      .replace(/\s*\|\s*Vimeo\s*$/i, "")
      .replace(/\s*-\s*Twitch\s*$/i, "")
      .trim();
  }

  function extractTitle(video, container) {
    if (video) {
      const vTitle = video.getAttribute("title") || video.getAttribute("aria-label");
      if (vTitle && vTitle.trim()) return cleanTitle(vTitle);
    }
    if (container) {
      const cTitle = container.getAttribute("data-title") || container.getAttribute("aria-label");
      if (cTitle && cTitle.trim()) return cleanTitle(cTitle);
    }
    const ogTitle = document.querySelector('meta[property="og:title"]');
    if (ogTitle && ogTitle.content && ogTitle.content.trim()) {
      return cleanTitle(ogTitle.content);
    }
    const twitterTitle = document.querySelector('meta[name="twitter:title"]');
    if (twitterTitle && twitterTitle.content && twitterTitle.content.trim()) {
      return cleanTitle(twitterTitle.content);
    }
    if (document.title && document.title.trim()) {
      return cleanTitle(document.title);
    }
    return "";
  }

  function extractQualityBadge(video) {
    if (!video) return "";
    const w = video.videoWidth || 0;
    const h = video.videoHeight || 0;

    if (w >= 3840 || h >= 2160) return "4K";
    if (w >= 2560 || h >= 1440) return "1440p";
    if (w >= 1920 || h >= 1080) return "1080p FHD";
    if (w >= 1280 || h >= 720) return "720p HD";
    return "";
  }

  function analyzeAndDispatch(video, container, triggerSource) {
    if (!video) return;

    lastTrackedVideo = video;
    const rawSrc = extractVideoSource(video);

    let isSupported = false;
    let reason = "UNKNOWN";

    const isHls =
      rawSrc.includes(".m3u8") ||
      (video.currentSrc && video.currentSrc.includes(".m3u8")) ||
      (video.canPlayType && video.canPlayType("application/vnd.apple.mpegurl") !== "");
    const isLive = isHls || video.duration === Infinity || (video.duration <= 0 && !video.paused);

    if (!rawSrc) {
      isSupported = false;
      reason = "NO_SRC_ATTRIBUTE";
    } else if (rawSrc.startsWith("blob:")) {
      isSupported = false;
      reason = "MSE_BLOB_STREAM";
    } else if (video.mediaKeys) {
      isSupported = false;
      reason = "DRM_EME_ENCRYPTED";
    } else if (isHls) {
      isSupported = true;
      reason = "DIRECT_HLS_MANIFEST";
    } else if (rawSrc.startsWith("http://") || rawSrc.startsWith("https://") || rawSrc.startsWith("file://")) {
      isSupported = true;
      reason = "DIRECT_HTTP_STREAM";
    } else {
      isSupported = false;
      reason = "UNSUPPORTED_SCHEME";
    }

    const title = extractTitle(video, container);
    const qualityBadge = extractQualityBadge(video);

    const payload = {
      action: "FULLSCREEN_ENTER",
      isSupported: isSupported,
      reason: reason,
      trigger: triggerSource,
      src: rawSrc,
      currentTime: Number.isFinite(video.currentTime) ? video.currentTime : 0,
      duration: Number.isFinite(video.duration) ? video.duration : -1,
      isLive: isLive,
      paused: !!video.paused,
      muted: !!video.muted,
      volume: typeof video.volume === "number" && Number.isFinite(video.volume) ? video.volume : 1.0,
      videoWidth: Number.isFinite(video.videoWidth) ? Math.round(video.videoWidth) : 0,
      videoHeight: Number.isFinite(video.videoHeight) ? Math.round(video.videoHeight) : 0,
      title: title,
      poster: video.poster ? resolveAbsoluteUrl(video.poster) : "",
      qualityBadge: qualityBadge,
      pageUrl: document.location ? document.location.href : "",
    };

    try {
      const port = getOrCreatePort();
      if (port) {
        port.postMessage(payload);
      }
    } catch (e) {}

    try {
      browser.runtime.sendNativeMessage(EXTENSION_PORT, payload);
    } catch (e) {}
  }

  function calculateVideoArea(v) {
    if (!v) return 0;
    const domArea = (v.offsetWidth || 0) * (v.offsetHeight || 0);
    const videoArea = (v.videoWidth || 0) * (v.videoHeight || 0);
    return Math.max(domArea, videoArea);
  }

  function findActiveVideo(fsElement) {
    if (!fsElement) return null;

    // 1. Direct video element
    if (fsElement.tagName && fsElement.tagName.toLowerCase() === "video") {
      return fsElement;
    }

    // 2. Videos inside the fullscreen container
    const innerVideos = Array.from(fsElement.querySelectorAll("video")).filter((v) => {
      return (v.offsetWidth > 40 && v.offsetHeight > 40) || v.videoWidth > 0 || !v.paused;
    });

    if (innerVideos.length > 0) {
      const playing = innerVideos.filter((v) => !v.paused);
      if (playing.length === 1) return playing[0];
      if (playing.length > 1) {
        playing.sort((a, b) => calculateVideoArea(b) - calculateVideoArea(a));
        return playing[0];
      }
      innerVideos.sort((a, b) => calculateVideoArea(b) - calculateVideoArea(a));
      return innerVideos[0];
    }

    // 3. Videos in closest player container (for Video.js, Plyr, JWPlayer wrapper)
    const playerWrapper = fsElement.closest(".video-player, .player, [data-player], .jwplayer, .vjs-player, .plyr, #player");
    if (playerWrapper) {
      const wrapperVideos = Array.from(playerWrapper.querySelectorAll("video"));
      if (wrapperVideos.length > 0) {
        const playing = wrapperVideos.filter((v) => !v.paused);
        if (playing.length > 0) return playing[0];
        return wrapperVideos[0];
      }
    }

    // 4. Global document fallback for multiple videos
    const allVideos = Array.from(document.querySelectorAll("video")).filter((v) => {
      return (v.offsetWidth > 50 && v.offsetHeight > 50) || v.videoWidth > 0 || !v.paused;
    });

    const activePlaying = allVideos.filter((v) => !v.paused);
    if (activePlaying.length === 1) return activePlaying[0];
    if (activePlaying.length > 1) {
      activePlaying.sort((a, b) => calculateVideoArea(b) - calculateVideoArea(a));
      return activePlaying[0];
    }

    if (allVideos.length > 0) {
      allVideos.sort((a, b) => calculateVideoArea(b) - calculateVideoArea(a));
      return allVideos[0];
    }

    return null;
  }

  function onFullscreenChanged() {
    const fsEl = document.fullscreenElement || document.webkitFullscreenElement || document.mozFullScreenElement;
    if (fsEl) {
      const video = findActiveVideo(fsEl);
      if (video) {
        analyzeAndDispatch(video, fsEl, "FULLSCREEN_API");
      }
    } else {
      try {
        const port = getOrCreatePort();
        if (port) {
          port.postMessage({ action: "FULLSCREEN_EXIT" });
        }
        browser.runtime.sendNativeMessage(EXTENSION_PORT, { action: "FULLSCREEN_EXIT" });
      } catch (e) {}
    }
  }

  document.addEventListener("fullscreenchange", onFullscreenChanged, true);
  document.addEventListener("webkitfullscreenchange", onFullscreenChanged, true);
  document.addEventListener("mozfullscreenchange", onFullscreenChanged, true);

  // Hook requestFullscreen on HTMLVideoElement to capture direct video user intent
  if (typeof HTMLVideoElement !== "undefined" && HTMLVideoElement.prototype) {
    const origVideoRequestFs = HTMLVideoElement.prototype.requestFullscreen ||
      HTMLVideoElement.prototype.webkitRequestFullscreen ||
      HTMLVideoElement.prototype.mozRequestFullScreen;
    if (origVideoRequestFs) {
      HTMLVideoElement.prototype.requestFullscreen = function () {
        analyzeAndDispatch(this, this, "VIDEO_PROTOTYPE_REQUEST");
        return origVideoRequestFs.apply(this, arguments);
      };
    }
  }

  // Hook requestFullscreen on Element prototype to capture custom JS player wrappers (Video.js, Plyr, JWPlayer)
  if (typeof Element !== "undefined" && Element.prototype) {
    const origElRequestFs = Element.prototype.requestFullscreen ||
      Element.prototype.webkitRequestFullscreen ||
      Element.prototype.mozRequestFullScreen;
    if (origElRequestFs) {
      Element.prototype.requestFullscreen = function () {
        const video = findActiveVideo(this);
        if (video) {
          analyzeAndDispatch(video, this, "ELEMENT_PROTOTYPE_REQUEST");
        }
        return origElRequestFs.apply(this, arguments);
      };
    }
  }

  const AUDIO_EXTENSIONS = [".mp3", ".m4a", ".aac", ".flac", ".wav", ".wave", ".ogg", ".oga", ".opus", ".weba"];
  const VIDEO_EXTENSIONS = [".mp4", ".m4v", ".mkv", ".webm", ".mov", ".3gp", ".ts", ".m3u8"];

  function isAudioExtension(url) {
    if (!url) return false;
    const clean = url.split("?")[0].split("#")[0].toLowerCase();
    return AUDIO_EXTENSIONS.some((ext) => clean.endsWith(ext));
  }

  function isVideoExtension(url) {
    if (!url) return false;
    const clean = url.split("?")[0].split("#")[0].toLowerCase();
    return VIDEO_EXTENSIONS.some((ext) => clean.endsWith(ext));
  }

  function isMediaUrl(url) {
    return isAudioExtension(url) || isVideoExtension(url);
  }

  // Direct audio document detection (e.g. standalone .mp3, .m4a, .aac, .flac, .wav, .wave, .ogg, .opus)
  function checkDirectAudioDocument() {
    const loc = (document.location ? document.location.href : "").split('?')[0].split('#')[0].toLowerCase();
    const isDirectAudio = AUDIO_EXTENSIONS.some((ext) => loc.endsWith(ext));
    if (isDirectAudio) {
      const audioEl = document.querySelector("audio, video");
      const title = cleanTitle(document.title) || loc.substring(loc.lastIndexOf("/") + 1);
      const payload = {
        action: "FULLSCREEN_ENTER",
        isSupported: true,
        reason: "DIRECT_AUDIO_DOCUMENT",
        trigger: "DIRECT_AUDIO_PAGE",
        src: document.location.href,
        currentTime: audioEl && Number.isFinite(audioEl.currentTime) ? audioEl.currentTime : 0,
        duration: audioEl && Number.isFinite(audioEl.duration) ? audioEl.duration : -1,
        isLive: false,
        paused: audioEl ? !!audioEl.paused : false,
        muted: audioEl ? !!audioEl.muted : false,
        volume: audioEl && typeof audioEl.volume === "number" ? audioEl.volume : 1.0,
        videoWidth: 0,
        videoHeight: 0,
        title: title,
        poster: "",
        qualityBadge: "Lossless Audio",
        pageUrl: document.location.href,
      };
      setTimeout(() => {
        try {
          const port = getOrCreatePort();
          if (port) port.postMessage(payload);
        } catch (e) {}
        try {
          browser.runtime.sendNativeMessage(EXTENSION_PORT, payload);
        } catch (e) {}
      }, 300);
    }
  }

  // Intercept direct user clicks on links pointing to media files (e.g. .wav, .mp3, etc.)
  function handleMediaLinkClick(event) {
    if (event.button !== 0 || event.altKey || event.ctrlKey || event.metaKey || event.shiftKey) {
      return;
    }

    const anchor = event.target && event.target.closest ? event.target.closest("a[href]") : null;
    if (!anchor) return;

    const rawHref = anchor.getAttribute("href");
    if (!rawHref || rawHref.startsWith("#") || rawHref.startsWith("javascript:")) return;

    const fullUrl = resolveAbsoluteUrl(rawHref);
    if (!fullUrl || !isMediaUrl(fullUrl)) return;

    const isAudio = isAudioExtension(fullUrl);
    const linkText = (anchor.textContent || "").trim();
    const linkTitle = anchor.getAttribute("title") || anchor.getAttribute("aria-label") || linkText;
    const fallbackFilename = fullUrl.substring(fullUrl.lastIndexOf("/") + 1).split("?")[0].split("#")[0];
    const title = cleanTitle(linkTitle) || decodeURIComponent(fallbackFilename);

    const payload = {
      action: "FULLSCREEN_ENTER",
      isSupported: true,
      reason: isAudio ? "DIRECT_AUDIO_LINK_CLICK" : "DIRECT_VIDEO_LINK_CLICK",
      trigger: "LINK_CLICK",
      src: fullUrl,
      currentTime: 0,
      duration: -1,
      isLive: false,
      paused: false,
      muted: false,
      volume: 1.0,
      videoWidth: 0,
      videoHeight: 0,
      title: title,
      poster: "",
      qualityBadge: isAudio ? "Lossless Audio" : "",
      pageUrl: document.location ? document.location.href : "",
    };

    let dispatched = false;
    try {
      const port = getOrCreatePort();
      if (port && !port.error) {
        port.postMessage(payload);
        dispatched = true;
      }
    } catch (e) {
      activePort = null;
      console.warn("[ClarusTheater] Port dispatch failed:", e);
    }

    if (!dispatched) {
      try {
        if (typeof browser !== "undefined" && browser.runtime && typeof browser.runtime.sendNativeMessage === "function") {
          const promise = browser.runtime.sendNativeMessage(EXTENSION_PORT, payload);
          if (promise && typeof promise.catch === "function") {
            promise.catch((err) => {
              console.warn("[ClarusTheater] Native messaging dispatch failed asynchronously:", err);
            });
          }
          dispatched = true;
        }
      } catch (e) {
        console.warn("[ClarusTheater] Native messaging dispatch failed:", e);
      }
    }

    if (dispatched) {
      event.preventDefault();
      event.stopPropagation();
    }
  }

  document.addEventListener("click", handleMediaLinkClick, true);

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", checkDirectAudioDocument);
  } else {
    checkDirectAudioDocument();
  }
})();
