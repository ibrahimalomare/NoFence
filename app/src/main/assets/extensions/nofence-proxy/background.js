/*
 * NoFence Built-In WebExtension Proxy & Fail-Closed Enforcer
 * Communicates with GeckoEngineProvider via GeckoView native messaging ("nofence_proxy_channel").
 * Enforces:
 * - DIRECT mode: normal routing
 * - TOR mode: SOCKS5 proxy with proxyDNS=true; if Tor is not Connected, returns fail-closed blackhole proxy
 * - CUSTOM_PROXY mode: SOCKS5 / HTTP / HTTPS proxy with proxyDNS and optional Proxy-Authorization; if unavailable/error, fails closed.
 */
'use strict';

let currentRoutingState = {
  mode: "DIRECT",          // "DIRECT" | "TOR" | "CUSTOM_PROXY"
  failClosedBlock: false,  // true when TOR or CUSTOM_PROXY is selected but not ready/verified
  proxyType: "direct",     // "socks" | "http" | "https" | "direct"
  host: "127.0.0.1",
  port: 9050,
  username: "",
  password: "",
  proxyDns: true,
  httpsFirst: true
};

const port = browser.runtime.connectNative("nofence_proxy_channel");

port.onMessage.addListener((message) => {
  if (message && message.type === "UPDATE_ROUTING") {
    currentRoutingState = {
      mode: message.mode || "DIRECT",
      failClosedBlock: Boolean(message.failClosedBlock),
      proxyType: message.proxyType || "direct",
      host: message.host || "127.0.0.1",
      port: Number(message.port) || 9050,
      username: message.username || "",
      password: message.password || "",
      proxyDns: message.proxyDns !== undefined ? Boolean(message.proxyDns) : true,
      httpsFirst: message.httpsFirst !== undefined ? Boolean(message.httpsFirst) : true
    };
    port.postMessage({
      status: "ACK",
      appliedMode: currentRoutingState.mode,
      failClosedBlock: currentRoutingState.failClosedBlock
    });
  }
});

browser.proxy.onRequest.addListener(
  (requestInfo) => {
    const url = requestInfo.url || "";
    if (url.startsWith("about:") || url.startsWith("moz-extension:")) {
      return { type: "direct" };
    }

    // Strict fail-closed enforcement: never leak direct traffic when Tor or Proxy is not ready
    if (currentRoutingState.mode !== "DIRECT" && currentRoutingState.failClosedBlock) {
      return {
        type: "socks",
        host: "127.0.0.1",
        port: 1,
        proxyDNS: true,
        failoverTimeout: 1
      };
    }

    if (currentRoutingState.mode === "DIRECT") {
      return { type: "direct" };
    }

    if (currentRoutingState.proxyType === "socks") {
      const rule = {
        type: "socks",
        host: currentRoutingState.host,
        port: currentRoutingState.port,
        proxyDNS: currentRoutingState.proxyDns,
        failoverTimeout: 5
      };
      if (currentRoutingState.username) {
        rule.username = currentRoutingState.username;
        rule.password = currentRoutingState.password || "";
      }
      return rule;
    }

    if (currentRoutingState.proxyType === "http" || currentRoutingState.proxyType === "https") {
      const rule = {
        type: currentRoutingState.proxyType,
        host: currentRoutingState.host,
        port: currentRoutingState.port,
        proxyDNS: currentRoutingState.proxyDns,
        failoverTimeout: 5
      };
      if (currentRoutingState.username && currentRoutingState.password) {
        const credentials = btoa(currentRoutingState.username + ":" + currentRoutingState.password);
        rule.proxyAuthorizationHeader = "Basic " + credentials;
      }
      return rule;
    }

    // Fallback safety for non-direct mode: fail closed
    return {
      type: "socks",
      host: "127.0.0.1",
      port: 1,
      proxyDNS: true
    };
  },
  { urls: ["<all_urls>"] }
);

// Handle HTTP/HTTPS proxy 407 authentication challenges automatically when credentials exist
browser.webRequest.onAuthRequired.addListener(
  (details) => {
    if (!details.isProxy) {
      return {};
    }
    if (currentRoutingState.username) {
      return {
        authCredentials: {
          username: currentRoutingState.username,
          password: currentRoutingState.password || ""
        }
      };
    }
    return {};
  },
  { urls: ["<all_urls>"] },
  ["blocking"]
);
