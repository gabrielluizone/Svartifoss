# Draft: request to matejdro for a GPLv3 §7 linking exception

**Status:** drafted, not yet sent. Send when able. Not a blocker for shipping —
if it is declined or goes unanswered, the fallback is the "System Library"
reasoning already stated in [`../LICENSING.md`](../LICENSING.md).

## Why

Svartifoss is a GPLv3 fork of [Music Center for
Wear](https://github.com/matejdro/WearMusicCenter). matejdro holds copyright on
the inherited code, which is plain GPLv3 with no linking exception. Svartifoss
(like WMC) links the proprietary Google Play Services (Wearable Data Layer API)
and Firebase client libraries. `LICENSING.md` already carries a §7 additional
permission for **Gabriel Luiz's** contributions; this request asks matejdro to
add the same for **his** portion, which closes the one remaining gap in the GPL
story before the paid Play Store listing.

An additional permission only ever *grants* rights — it cannot worsen anyone's
position — and any downstream recipient may strip it out again (GPLv3 §7).

## Do this first

Confirm matejdro is effectively the sole copyright holder before relying on a
single "yes":

```sh
git clone https://github.com/matejdro/WearMusicCenter && cd WearMusicCenter
git shortlog -sne --all
```

Also skim the GitHub contributors graph. If anyone else contributed a
non-trivial, still-present amount of code, their agreement matters too. WMC
looks near-single-author, but verify.

## Exception text (for matejdro to adopt)

Appended to `COPYING`, or in a `LICENSE-EXCEPTION` file, in WearMusicCenter:

> **Additional permission under GNU GPL version 3 section 7**
>
> As a special exception, the copyright holders of Music Center for Wear give
> you permission to link or combine this program, or a work based on it, with
> the proprietary Google Play Services and Firebase client libraries —
> libraries in the `com.google.android.gms.*` and `com.google.firebase.*`
> namespaces, including the Wearable Data Layer API — or with a modified
> version of those libraries, and to convey the resulting work. The terms of
> the GNU GPL version 3 continue to apply to the program itself. You may remove
> this exception from your copy, or carry it forward, as permitted by section 7
> of the GNU GPL version 3.

## GitHub issue version (preferred — leaves a public record)

**Title:** Request: GPLv3 §7 additional permission for Google Play Services / Firebase linking

**Body:**

> Hi @matejdro,
>
> I maintain [Svartifoss](https://github.com/gabrielluizone/Svartifoss), a
> GPLv3 continuation of Music Center for Wear — thanks for the original work.
>
> Like WMC, Svartifoss depends on the proprietary Google Play Services
> (Wearable Data Layer API) and Firebase client libraries; they're unavoidable
> for phone↔watch communication. Strictly read, conveying a GPLv3 binary linked
> against those proprietary libraries needs a GPLv3 §7 additional permission
> from the copyright holders. I've added one for my own changes, but the code
> inherited from WMC is still plain GPLv3.
>
> Would you be willing to add the same additional permission to WMC? It only
> *grants* permission (never restricts), anyone downstream can remove it again,
> and it changes nothing else about WMC's license. Suggested text, adapt freely
> — e.g. appended to `COPYING`:
>
> > As a special exception, the copyright holders of Music Center for Wear give
> > you permission to link or combine this program, or a work based on it, with
> > the proprietary Google Play Services and Firebase client libraries
> > (`com.google.android.gms.*` / `com.google.firebase.*`, including the
> > Wearable Data Layer API), or a modified version of those libraries, and to
> > convey the resulting work. The GNU GPL version 3 continues to apply to the
> > program itself. You may remove this exception from your copy, or carry it
> > forward, as permitted by GPLv3 section 7.
>
> A "yes, go ahead" reply here, or a commit adding it, is all I need. Thanks
> either way.

## Email version (fallback)

> Subject: Music Center for Wear — small GPLv3 licensing request
>
> Hi matejdro,
>
> I maintain Svartifoss (github.com/gabrielluizone/Svartifoss), a GPLv3
> continuation of Music Center for Wear. Thanks for the original.
>
> WMC and Svartifoss both link the proprietary Google Play Services (Wearable
> Data Layer API) and Firebase libraries, which strictly speaking needs a
> GPLv3 section 7 additional permission from the copyright holders to convey the
> combined binary. I've added one for my own changes; the inherited WMC code is
> still plain GPLv3.
>
> Would you add the same permission to WMC? It only grants rights, it's
> removable downstream, and it changes nothing else about the license.
> Suggested wording (adapt freely), e.g. appended to COPYING:
>
> [exception text above]
>
> A short "I agree" by reply is enough, or a commit if you prefer. Thanks.

## If declined or unanswered

No further action needed to ship. `LICENSING.md` documents the position:
Google Play Services is a pre-installed platform component on the certified
Android/Wear OS devices Svartifoss targets (the GPLv3 §1 "System Library"
situation), and the dependency was inherited from WMC, which matejdro himself
shipped linked against the Wearable Data Layer API.
