-- ── Flood zones: approximate Accra hotspots, ±0.01° bounding boxes ─────────
INSERT INTO flood_zones (name, min_lat, max_lat, min_lng, max_lng) VALUES
  ('Kaneshie',              5.556700, 5.576700, -0.243300, -0.223300),
  ('Weija',                 5.546000, 5.566000, -0.330000, -0.310000),
  ('Adabraka',              5.548000, 5.568000, -0.218000, -0.198000),
  ('Kwame Nkrumah Circle',  5.561500, 5.581500, -0.223300, -0.203300);

-- ── 50 Ghana-specific safety tips ───────────────────────────────────────────
-- Columns: tip_text, category, applies_property_type, applies_asset_category,
--          applies_season, applies_flood_zone, min_category_value, priority

-- FIRE (12) — market-stall and shop fire prevention, wiring, electronics
INSERT INTO tip_templates (tip_text, category, applies_property_type, applies_asset_category, applies_season, applies_flood_zone, min_category_value, priority) VALUES
  ('Densely packed stalls burn fast. Keep a 1-metre clear corridor between your stock and your neighbour''s, and agree an exit route with the stalls beside you — in Kantamanto-style markets the gap you leave is the firebreak.', 'FIRE', 'COMMERCIAL', 'CLOTHING_STOCK', NULL, NULL, NULL, 1),
  ('Have an electrician check your shop wiring before you add another freezer, fan or sewing machine to the line. Most Accra market fires start from one overloaded socket strip feeding five appliances.', 'FIRE', 'COMMERCIAL', NULL, NULL, NULL, NULL, 2),
  ('Keep a working dry-powder fire extinguisher within reach of the door, not buried behind stock. Check the pressure gauge monthly — a flat extinguisher is just decoration when bales of clothing catch.', 'FIRE', 'COMMERCIAL', 'CLOTHING_STOCK', NULL, NULL, NULL, 2),
  ('No coal pot, candle or open flame inside or behind a stall holding clothing stock. If you cook at the shop, do it a full stall-width away from anything that burns, and douse the coals before you close.', 'FIRE', 'COMMERCIAL', 'CLOTHING_STOCK', NULL, NULL, NULL, 2),
  ('Switch off and unplug shop appliances at close of day — don''t just turn the wall switch. Night-time power surges after ECG restores supply are a classic cause of fires in locked, empty shops.', 'FIRE', 'COMMERCIAL', 'ELECTRONICS', NULL, NULL, NULL, 3),
  ('Use a surge protector (not a plain extension board) for TVs, fridges and sound systems. After a power cut, wait a few minutes before switching big appliances back on — the first minutes of restored power are the roughest.', 'FIRE', NULL, 'ELECTRONICS', NULL, NULL, NULL, 4),
  ('Don''t run cables under carpets or behind stacked stock where heat builds and fraying goes unseen. Lift and inspect any cable older than five years — brittle insulation is fuel.', 'FIRE', NULL, NULL, NULL, NULL, NULL, 5),
  ('If you use LPG at home, keep the cylinder outside the kitchen if possible and check the hose with soapy water for leaks every few months. Replace any hose that bubbles immediately.', 'FIRE', 'RESIDENTIAL', NULL, NULL, NULL, NULL, 4),
  ('Photograph your shop''s meter board and main wiring after any electrical work. If a fire happens, dated photos showing professional wiring strengthen your insurance position.', 'FIRE', 'COMMERCIAL', NULL, NULL, NULL, NULL, 5),
  ('Generators belong outside, on bare ground, away from stock and fuel cans. Refuel only when the engine is cool — petrol on a hot exhaust has destroyed whole stall rows.', 'FIRE', 'COMMERCIAL', NULL, NULL, NULL, NULL, 3),
  ('Store sewing machines, pressing irons and other heat appliances unplugged and on a hard surface overnight. An iron left face-down on a wooden table is a slow fuse.', 'FIRE', 'COMMERCIAL', 'MACHINERY', NULL, NULL, NULL, 5),
  ('Know your market''s fire hydrant or water point and the GNFS number for your area (call 192). The first ten minutes decide whether one stall burns or fifty.', 'FIRE', 'COMMERCIAL', NULL, NULL, NULL, NULL, 5);

-- FLOOD (10) — flood-zone and rainy-season focused
INSERT INTO tip_templates (tip_text, category, applies_property_type, applies_asset_category, applies_season, applies_flood_zone, min_category_value, priority) VALUES
  ('Your property sits in a known Accra flood area. Raise all ground-level stock and appliances at least 30 cm on pallets or concrete blocks — the first June downpour gives no warning.', 'FLOOD', NULL, NULL, 'RAINY', TRUE, NULL, 1),
  ('Before the rains peak, photograph everything at floor level: stock, furniture, machines. Time-stamped evidence taken BEFORE a flood is what makes a water-damage claim succeed.', 'FLOOD', NULL, NULL, 'RAINY', TRUE, NULL, 1),
  ('Clear the gutter and drain in front of your property every week during the rainy season — and agree with neighbours to do the whole stretch. A choked drain two doors away still ends up in your room.', 'FLOOD', NULL, NULL, 'RAINY', TRUE, NULL, 2),
  ('Move documents, receipts and electronics to the highest shelf in the room, not the lowest drawer. In a flash flood you get minutes, not hours — store things high by default.', 'FLOOD', NULL, 'DOCUMENTS', NULL, TRUE, NULL, 2),
  ('Keep electronics off the floor permanently if you are in a flood-prone area: wall-mount the TV, put the fridge on a raised platform, keep chargers and extensions on tables.', 'FLOOD', NULL, 'ELECTRONICS', NULL, TRUE, NULL, 2),
  ('Sandbags or a half-block cement sill at the door buys you 15–20 cm of protection. For shops near Circle, Kaneshie or Weija, that sill has saved whole seasons of stock.', 'FLOOD', 'COMMERCIAL', NULL, 'RAINY', TRUE, NULL, 2),
  ('Check your roof and ceiling before April: cracked sheets, loose nails and blocked roof gutters turn one rainstorm into an indoor flood. A small repair in March is cheap; a soaked ceiling in June is not.', 'FLOOD', 'RESIDENTIAL', NULL, 'RAINY', NULL, NULL, 3),
  ('Know where your electricity main switch is and keep the path to it clear. If water enters, cut power FIRST — then save property. Electrocution, not water, is the killer in flooded rooms.', 'FLOOD', NULL, NULL, 'RAINY', NULL, NULL, 3),
  ('Store cement, fertiliser and anything that spoils with moisture on the highest pallet, double-wrapped in plastic. Even without standing water, rainy-season humidity wicks up through bare floors.', 'FLOOD', 'COMMERCIAL', NULL, 'RAINY', NULL, NULL, 4),
  ('After any flooding, photograph the water line on the wall and every damaged item BEFORE cleaning up. Document first, clean second — your dossier needs the mess.', 'FLOOD', NULL, NULL, NULL, TRUE, NULL, 3);

-- THEFT (8) — high-value electronics/machinery focus
INSERT INTO tip_templates (tip_text, category, applies_property_type, applies_asset_category, applies_season, applies_flood_zone, min_category_value, priority) VALUES
  ('You hold significant electronics value. Record every serial number (phone, TV, laptop, decoder) in your AssetShield photos — a serial in the photo is what lets police and insurers identify recovered goods.', 'THEFT', NULL, 'ELECTRONICS', NULL, NULL, 5000.00, 2),
  ('High-value machines deserve their own lock: chain heavy equipment to a wall anchor or floor bolt, not just the door padlock. Thieves carry bolt cutters for doors; few cut anchored chains.', 'THEFT', NULL, 'MACHINERY', NULL, NULL, 5000.00, 2),
  ('Don''t advertise your stock: keep cartons of new electronics out of street view and break down branded boxes before putting them outside. An empty 65-inch TV box at the kerb is an invitation.', 'THEFT', NULL, 'ELECTRONICS', NULL, NULL, 3000.00, 3),
  ('Change padlocks when a shop attendant or tenant leaves, and never share one key among many people. Most shop theft in Ghana involves a key that was copied, not a door that was broken.', 'THEFT', 'COMMERCIAL', NULL, NULL, NULL, NULL, 3),
  ('Photograph your burglar-proofing (window bars, door grilles) as part of your evidence. It proves forced entry when bars are cut — and that distinction can decide a theft claim.', 'THEFT', 'RESIDENTIAL', NULL, NULL, NULL, NULL, 4),
  ('A small UPS or hidden socket for your CCTV/alarm keeps it running through ECG outages — break-ins cluster during light-off nights. Even a dummy camera repositions risk to the next street.', 'THEFT', NULL, 'ELECTRONICS', NULL, NULL, NULL, 4),
  ('For rented premises, agree in writing with the landlord who replaces locks and bars after a break-in, and keep your own inventory separate from the landlord''s fittings.', 'THEFT', 'RENTAL', NULL, NULL, NULL, NULL, 5),
  ('Vary your closing routine. Counting cash at the same visible spot at the same hour every evening builds an audience; bank takings during the day when you can.', 'THEFT', 'COMMERCIAL', NULL, NULL, NULL, NULL, 5);

-- SEASONAL (10) — Harmattan and rainy-season habits
INSERT INTO tip_templates (tip_text, category, applies_property_type, applies_asset_category, applies_season, applies_flood_zone, min_category_value, priority) VALUES
  ('Harmattan air turns everything to tinder. Damp down dust around wooden stalls in the morning, and treat every spark — cigarette, coal pot, welding next door — as a live threat until the rains return.', 'SEASONAL', 'COMMERCIAL', NULL, 'HARMATTAN', NULL, NULL, 2),
  ('During Harmattan, dust works into fans, fridge compressors and laptops and makes them overheat. Cover electronics overnight with cloth (not airtight plastic) and blow out vents monthly.', 'SEASONAL', NULL, 'ELECTRONICS', 'HARMATTAN', NULL, NULL, 3),
  ('Dry Harmattan weeks are when bush and rubbish fires jump walls. Clear dry leaves and refuse from around your property line — a clean 2-metre perimeter stops most spreading fires.', 'SEASONAL', 'RESIDENTIAL', NULL, 'HARMATTAN', NULL, NULL, 3),
  ('Static and dry air make fuel vapours more dangerous in Harmattan. Store petrol for the generator in proper closed containers, outside, never in the shop with the stock.', 'SEASONAL', 'COMMERCIAL', NULL, 'HARMATTAN', NULL, NULL, 3),
  ('Harmattan haze hides smoke. Smell something burning? Investigate immediately rather than assuming it''s a neighbour''s rubbish — early minutes are everything in the dry season.', 'SEASONAL', NULL, NULL, 'HARMATTAN', NULL, NULL, 5),
  ('Book your roofer in February or March, not when the sky is already grey. Replace rusted sheets, re-nail loose ones and seal flashings — Accra''s first big storm finds every weak sheet.', 'SEASONAL', 'RESIDENTIAL', NULL, 'RAINY', NULL, NULL, 3),
  ('Service your fridge and freezer gaskets before the rainy season. Power cuts plus humidity spoil stock fast — a tight gasket keeps the cold through a 4-hour outage.', 'SEASONAL', 'COMMERCIAL', NULL, 'RAINY', NULL, NULL, 4),
  ('Rainy season is mould season for fabric. Air out stored clothing stock weekly and keep silica or charcoal sachets in closed boxes — mildew writes off bales quietly.', 'SEASONAL', 'COMMERCIAL', 'CLOTHING_STOCK', 'RAINY', NULL, NULL, 3),
  ('Trim tree branches hanging over the roof before the storms. Falling limbs and wind-whipped branches cause more rainy-season roof damage in residential Accra than the rain itself.', 'SEASONAL', 'RESIDENTIAL', NULL, 'RAINY', NULL, NULL, 4),
  ('Lightning season: unplug aerials and outdoor antenna leads during heavy storms. A strike on the pole travels straight into the TV — unplugging is free insurance.', 'SEASONAL', NULL, 'ELECTRONICS', 'RAINY', NULL, NULL, 4);

-- GENERAL (10) — documentation discipline
INSERT INTO tip_templates (tip_text, category, applies_property_type, applies_asset_category, applies_season, applies_flood_zone, min_category_value, priority) VALUES
  ('Photograph the receipt the same day you buy anything valuable, and attach it to the asset in AssetShield. Thermal paper receipts from Accra shops fade to blank in months — the photo outlives the paper.', 'GENERAL', NULL, NULL, NULL, NULL, NULL, 4),
  ('Re-document after every significant purchase or restock. Ten minutes of photos after a buying trip keeps your evidence matching what''s actually in the room when you need to claim.', 'GENERAL', NULL, NULL, NULL, NULL, NULL, 4),
  ('Capture serial numbers and model plates in close-up, then one wide shot showing the item in your space. The pair — identity plus location — is what assessors trust.', 'GENERAL', NULL, 'ELECTRONICS', NULL, NULL, NULL, 5),
  ('Take documentation photos outdoors-facing or near a window when possible: GPS accuracy is far better with open sky, and accurate GPS is what ties your evidence to your property.', 'GENERAL', NULL, NULL, NULL, NULL, NULL, 6),
  ('Photograph inside cupboards, drawers and storerooms — not just the showroom face of the shop. Hidden stock is the stock everyone forgets to claim for.', 'GENERAL', 'COMMERCIAL', NULL, NULL, NULL, NULL, 5),
  ('Walk your property with the camera every quarter even if nothing is new: things move, wear and accumulate. A fresh quarterly sweep keeps your last-documented date close to reality.', 'GENERAL', NULL, NULL, NULL, NULL, NULL, 6),
  ('Keep land documents, tenancy agreements and insurance papers photographed AND stored off-site (or in the app). Paper kept only at the property burns or soaks with everything else.', 'GENERAL', NULL, 'DOCUMENTS', NULL, NULL, NULL, 4),
  ('When you add a big asset, note who sold it and their phone number in the description. A reachable seller is a second witness to value if a claim is questioned.', 'GENERAL', NULL, NULL, NULL, NULL, NULL, 6),
  ('Furniture counts: photograph beds, sofas and shelving with any maker''s label visible. Owners routinely under-claim furniture because it was never documented.', 'GENERAL', 'RESIDENTIAL', 'FURNITURE', NULL, NULL, NULL, 6),
  ('Invite a household member to help document — a second phone covers the rooms you skip. Shared documentation is faster and survives one lost phone.', 'GENERAL', 'RESIDENTIAL', NULL, NULL, NULL, NULL, 6);
