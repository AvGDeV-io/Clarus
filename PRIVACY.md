# Clarus Privacy Notice

*Last updated: October 2026. Applies to the Clarus beta.*

Clarus is a one-person beta project built on Firefox's engine. This page explains what it does with your data, and where I'm not certain, I say so.

## The short version

- Clarus has no accounts, no sign-in, and no sync.
- I don't run any servers that collect your data.
- Telemetry, usage pings, feature studies and crash reporting are all **off by default**.
- Your history, bookmarks, and settings stay on your device.

## What stays on your device

Browsing history, bookmarks, tabs, passwords you choose to save, downloads, and your Sidus Trail all live on your phone. I never receive them.

## What is switched off by default

On a fresh install I checked these settings and they were off:

- Send technical and interaction data: **off**
- Daily usage ping: **off**
- Allow feature studies: **off**
- Crash reports: **Never send**

Clarus has no data collection service of its own. There is a Data collection screen left over from Firefox that can still be reached through Settings search. If you turn something on there, that data would go to Mozilla's systems, because that's the only place the underlying code knows how to send it. Please leave those switches off if you don't want that.

## Connections Clarus makes

- **Websites you visit.** Obviously. Those sites have their own privacy policies.
- **Search.** Your search engine receives what you type into it, and suggestions if you have them turned on.
- **Mozilla Remote Settings.** On first launch the browser makes a request to Mozilla's Remote Settings service. It delivers security data such as certificate revocation lists and blocklists. I left it on because turning it off would make the browser less safe. It is not an analytics request.
- **Safe browsing.** The engine may check lists of known dangerous sites.
- **Extensions.** If you install extensions, they come from `addons.mozilla.org`, and each extension has its own behavior and permissions. Read what an extension asks for before you install it.

## Mozilla links in the app

Some "Learn more" links in Settings open Mozilla support pages, for example for tracking protection or HTTPS-only mode. Those features work the same way in Clarus, so I left the explainers in place. Opening one sends you to Mozilla's website, which has its own privacy policy.

## What I can't promise

Clarus is built from Mozilla's open source code, and it is a beta maintained by one person. I audited the settings and the code paths I could check, and everything above is what I found. I can't guarantee nothing was missed. If you see Clarus contacting something that isn't described here, please [open an issue](https://github.com/AvGDeV-io/Clarus/issues) and I'll look into it and update this page.

## Not affiliated with Mozilla

Clarus is independent. Mozilla doesn't make it, endorse it, or have any responsibility for it.

## Contact

Questions or concerns: [github.com/AvGDeV-io/Clarus/issues](https://github.com/AvGDeV-io/Clarus/issues)
