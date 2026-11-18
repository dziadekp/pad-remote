# Pad Remote

A one-page Bluetooth remote for a PitPat-T01 walking pad (DeerRun), so the PitPat app isn't needed.
Open it in Chrome on Android, tap Connect, pick the pad.

- Protocol: service `FFFF`, write `FF01` (with response), notify `FF02`.
- The pad sends status about once a second and must get a reply to each one, or it drops the link after about 10 seconds.
- Speed is in metres per hour (km/h × 1000), from 1000 to 6000. Distance is in metres. The pad shows mph.
- Command format credit: hdxsfbr/walkingpad, azmke/pitpat-treadmill-control.
