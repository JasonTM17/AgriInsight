# Generated dashboard visuals

Generated specifically for AgriInsight on 2026-07-22 with the built-in OpenAI
image-generation tool, then stripped and converted to WebP with ImageMagick.
No third-party stock image was used. These files are product/demo visuals, not
photographs from a customer farm and not ground-truth training data.

## Catalog

| File | Dimensions | Runtime use | Accessible description | SHA-256 |
|---|---:|---|---|---|
| `overview-fields.webp` | 1600 × 800 | Executive | Aerial Vietnamese rice and vegetable fields with irrigation, sensors, a small tractor, and greenhouses at sunrise. | `9b03711e10447f443e4045bb9f47ed0df59672ce2b840f6049a18763a6b4ee3f` |
| `farm-performance.webp` | 1440 × 810 | Farm Performance | Farm manager checks a tablet beside a rice field and irrigation channel while a worker inspects the crop. | `d81a0f16bfcfc19b20b8ce82ca9ec2a9118b62ed9199ff2089755b2a8cfcb41e` |
| `inventory-control.webp` | 1440 × 810 | Inventory | Two inventory workers verify sealed agricultural supplies and irrigation parts in an organized warehouse. | `d9d5138b40c0a2606de13fb21e6f177c0b7b254ba6e1a2ccce2a58354663604a` |
| `crop-health-evidence.webp` | 1200 × 900 | Crop Health | Close field view of a few rice leaves with localized brown lesions beside a compact sensor after rain. | `abb6da1a4315537e121ffa6a2f4f25db860bb9afe8d02db48f63c61c04d29f8e` |
| `data-quality-sensors.webp` | 1440 × 810 | Data Quality | Field technician validates a weather station and soil-sensor installation beside cultivated rows. | `5adb169fc0b4814171676b5b203fadc5b8c1011a7abf8a8428d0e4df2db5ff5b` |
| `cost-procurement.webp` | 1440 × 810 | Cost Analysis | Operations lead and supplier check harvested produce quantities beside crates, a platform scale, and a pallet trolley. | `44675bb309251d9f8dd70d98b100613cea4c24a39f446323fde95c4bf25dbaf7` |

## Final prompt set

All prompts requested realistic Vietnamese agricultural operations, restrained
Field Ledger colors, a wide composition with useful negative space, credible
equipment and human scale, natural documentary lighting, and no text, logo,
watermark, UI overlay, neon treatment, or staged stock-photo pose.

- **Executive:** geometric rice/vegetable fields, irrigation, small tractor,
  sensor posts, greenhouses, and an early-morning aerial view.
- **Farm Performance:** a manager using a rugged tablet beside rice rows and an
  irrigation channel, with one field worker in the distance.
- **Inventory:** an auditable agricultural input warehouse with sealed sacks,
  irrigation parts, pallet racks, receiving flow, and handheld scanning.
- **Crop Health:** a scientifically plausible localized rice-leaf stress close
  view with mostly healthy surrounding plants and a compact sensor.
- **Data Quality:** a technician comparing a handheld meter with installed
  weather/soil equipment after rain.
- **Cost Analysis:** a farm packing area where harvested produce quantities are
  checked beside a platform scale and material-handling equipment.

## Evidence boundary

`crop-health-evidence.webp` must always be labeled **AI-generated demo
evidence** wherever it is rendered. It cannot receive a production observation
ID, be used to train or validate a disease model, or support an agronomic claim.
Real evidence later requires capture provenance, timestamp, actor/device,
field/season scope, immutable object identity, and an approved retention policy.

## Social preview

`docs/assets/agriinsight-social-preview.jpg` is a 1280 × 640 crop of the
Executive source scene for GitHub/social metadata. Its SHA-256 is
`5328405d189afb7af662a013ea6ba64f89548898970235028ebfee2c82f91c47`.
