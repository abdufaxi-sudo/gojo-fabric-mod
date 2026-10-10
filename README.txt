GOJO ABILITIES v2 - Fabric 1.21.1 (Java 21)

QURMAQ: qovlugu GitHub-a yukle -> Actions -> build -> Run workflow -> Artifacts-dan jar-i endir.
Mods qovluguna at + Fabric API 0.102.0+1.21.1.

GUCLER:
Z Blue   - uzaqda mavi kure, 12 blok icinde her seyi cekir, merkezde oldurur, yerleri qirir
X Red    - kicik qirmizi top, baxdigin yere gedir, deyende partlayir (oldurur/itir/qirir)
C Purple - neheng benovseyi kure, sarj olur, sonra qarsisindaki hertseyi silir/oldurur
H Domain - 14 blok radiusda sonsuz bosluq: canlilar 6 san donur, ekran qarali ulduzlu olur
   (J Simple Domain basanlari domaine qarsi qoruyur)
V Infinity | B Reverse | N Black Flash | G Teleport | H Domain | J Simple Domain | K Six Eyes | M Menu

LAG OLARSA: Abilities.java -> DESTROY_BLOCKS = false  (blok qirmaz, FPS yaxsilasir)
OrbEntity.java -> BLOCKS_PER_TICK azalt.
DIQQET: DESTROY_BLOCKS=true olanda dunyani qirir (blok geri gelmir) - world backup al!
