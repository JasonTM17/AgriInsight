(() => {
  const operatingMonthly = [
    { month: "2025-01", cost: 7077947000, revenue: 0, profit: -7077947000 },
    { month: "2025-02", cost: 15426225000, revenue: 0, profit: -15426225000 },
    { month: "2025-03", cost: 24700717000, revenue: 0, profit: -24700717000 },
    { month: "2025-04", cost: 23928236000, revenue: 5831594000, profit: -18096642000 },
    { month: "2025-05", cost: 20891536000, revenue: 29123030000, profit: 8231494000 },
    { month: "2025-06", cost: 20136332000, revenue: 2339393000, profit: -17796939000 },
    { month: "2025-07", cost: 15522666000, revenue: 67641336000, profit: 52118670000 },
    { month: "2025-08", cost: 10763432000, revenue: 0, profit: -10763432000 },
    { month: "2025-09", cost: 9042128000, revenue: 154802276000, profit: 145760148000 },
    { month: "2025-10", cost: 2423062000, revenue: 101270971000, profit: 98847909000 },
    { month: "2025-11", cost: 1708725000, revenue: 0, profit: -1708725000 },
    { month: "2025-12", cost: 217491000, revenue: 33132282000, profit: 32914791000 },
    { month: "2026-01", cost: 7483616000, revenue: 0, profit: -7483616000 },
    { month: "2026-02", cost: 16753457000, revenue: 0, profit: -16753457000 },
    { month: "2026-03", cost: 22881962000, revenue: 0, profit: -22881962000 },
    { month: "2026-04", cost: 22444107000, revenue: 4750621000, profit: -17693486000 },
    { month: "2026-05", cost: 25699196000, revenue: 27780047000, profit: 2080851000 },
    { month: "2026-06", cost: 15223062000, revenue: 2894486000, profit: -12328576000 },
    { month: "2026-07", cost: 4656351000, revenue: 36203882000, profit: 31547531000 },
  ];

  const operatingDrivers = [
    { id: "bon-phan", name: "Bón phân", count: 96, material: 47305432000, labor: 14938554000, total: 62243986000, share: 23.314079, latest: { id: "ACT-2026-0015-010", at: "2026-06-07T15:03:00", farm: "Nông trại Biển Hồ", field: "Khu vực 4.3", season: "SEASON-2026-0015", crop: "Cà phê", material: "Phân NPK 16-16-8", materialCost: 187976000, laborCost: 59361000, total: 247337000 } },
    { id: "gieo-trong", name: "Gieo trồng", count: 96, material: 27974222000, labor: 22887997000, total: 50862219000, share: 19.05093, latest: { id: "ACT-2026-0015-008", at: "2026-05-22T13:49:00", farm: "Nông trại Biển Hồ", field: "Khu vực 4.3", season: "SEASON-2026-0015", crop: "Cà phê", material: "Hạt giống", materialCost: 114793000, laborCost: 93921000, total: 208714000 } },
    { id: "phun-thuoc", name: "Phun thuốc", count: 96, material: 27474853000, labor: 13532389000, total: 41007242000, share: 15.359654, latest: { id: "ACT-2026-0018-013", at: "2026-07-01T08:24:00", farm: "Nông trại Cửu Long", field: "Khu vực 5.2", season: "SEASON-2026-0018", crop: "Cà phê", material: "Chế phẩm sinh học", materialCost: 41925000, laborCost: 20649000, total: 62574000 } },
    { id: "tuoi-nuoc", name: "Tưới nước", count: 96, material: 15002212000, labor: 24477304000, total: 39479516000, share: 14.78743, latest: { id: "ACT-2026-0015-009", at: "2026-05-30T14:56:00", farm: "Nông trại Biển Hồ", field: "Khu vực 4.3", season: "SEASON-2026-0015", crop: "Cà phê", material: "Nước và điện tưới", materialCost: 62154000, laborCost: 101408000, total: 163562000 } },
    { id: "lam-co", name: "Làm cỏ", count: 96, material: 3618854000, labor: 26538237000, total: 30157091000, share: 11.295626, latest: { id: "ACT-2026-0015-011", at: "2026-06-15T06:10:00", farm: "Nông trại Biển Hồ", field: "Khu vực 4.3", season: "SEASON-2026-0015", crop: "Cà phê", material: "Dụng cụ làm cỏ", materialCost: 14496000, laborCost: 106308000, total: 120804000 } },
    { id: "bao-duong", name: "Bảo dưỡng", count: 96, material: 12616740000, labor: 13668129000, total: 26284869000, share: 9.845249, latest: { id: "ACT-2026-0021-014", at: "2026-07-09T09:31:00", farm: "Nông trại Bình Minh", field: "Khu vực 6.1", season: "SEASON-2026-0021", crop: "Cà phê", material: "Nhiên liệu và phụ tùng", materialCost: 63616000, laborCost: 68917000, total: 132533000 } },
    { id: "kiem-tra-sau-benh", name: "Kiểm tra sâu bệnh", count: 96, material: 1355625000, labor: 15589700000, total: 16945325000, share: 6.347033, latest: { id: "ACT-2026-0015-012", at: "2026-06-23T07:17:00", farm: "Nông trại Biển Hồ", field: "Khu vực 4.3", season: "SEASON-2026-0015", crop: "Cà phê", material: "Dụng cụ kiểm tra", materialCost: 5351000, laborCost: 61536000, total: 66887000 } },
  ];

  const farmComparison = [
    { code: "FARM-001", name: "Nông trại Cao Nguyên", province: "Đắk Lắk", seasons: 8, area: 411.44, budget: 79455599000, cost: 65770249000, variance: -13685350000, profit: 43627623000, margin: 39.879773, costHa: 159853803.714, costKg: 17439.999 },
    { code: "FARM-006", name: "Nông trại Bình Minh", province: "Lâm Đồng", seasons: 8, area: 382.26, budget: 72262890000, cost: 57900364000, variance: -14362526000, profit: 33524709000, margin: 36.669054, costHa: 151468539.737, costKg: 25304.913 },
    { code: "FARM-004", name: "Nông trại Biển Hồ", province: "Gia Lai", seasons: 8, area: 301.94, budget: 57818305000, cost: 48355718000, variance: -9462587000, profit: 47270347000, margin: 49.432492, costHa: 160150089.422, costKg: 14583.2 },
    { code: "FARM-002", name: "Nông trại Phù Sa", province: "An Giang", seasons: 8, area: 254.72, budget: 45130333000, cost: 37804947000, variance: -7325386000, profit: 29777584000, margin: 44.061067, costHa: 148417662.531, costKg: 15074.905 },
    { code: "FARM-003", name: "Nông trại Miền Đông", province: "Đồng Nai", seasons: 8, area: 340.64, budget: 37169952000, cost: 34682590000, variance: -2487362000, profit: 18679104000, margin: 35.004706, costHa: 101815964.068, costKg: 10474.681 },
    { code: "FARM-005", name: "Nông trại Cửu Long", province: "Cần Thơ", seasons: 8, area: 155.22, budget: 26772256000, cost: 22466380000, variance: -4305876000, profit: 25910303000, margin: 53.559486, costHa: 144738951.166, costKg: 16293.7 },
  ];

  const procurementMonthly = [
    { month: "2025-01", spend: 9449207100, transactions: 90 }, { month: "2025-04", spend: 5204339700, transactions: 90 },
    { month: "2025-07", spend: 5119996500, transactions: 90 }, { month: "2025-10", spend: 5117537000, transactions: 90 },
    { month: "2026-01", spend: 5249089900, transactions: 90 }, { month: "2026-04", spend: 5116625000, transactions: 90 },
    { month: "2026-07", spend: 6374878700, transactions: 108 },
  ];

  const supplierDrivers = [
    { id: "SUP-005", name: "Thiết bị Tưới Việt", province: "TP. Hồ Chí Minh", quality: 4.3, transactions: 82, spend: 5522901900, share: 13.266106, latest: { id: "INV-TX-0000345", date: "2026-07-16", farm: "Nông trại Cao Nguyên", warehouse: "Kho vật tư Nông trại Cao Nguyên", material: "Bộ bảo hộ lao động", quantity: 361.9, unit: "piece", unitCost: 441700, spend: 159868000, batch: "BATCH-0000345", expiry: "2029-07-15" } },
    { id: "SUP-007", name: "Bao bì Miền Tây", province: "Long An", quality: 4.2, transactions: 81, spend: 5347806200, share: 12.845523, latest: { id: "INV-TX-0001109", date: "2026-07-16", farm: "Nông trại Miền Đông", warehouse: "Kho vật tư Nông trại Miền Đông", material: "Bộ bảo hộ lao động", quantity: 316.573, unit: "piece", unitCost: 405200, spend: 128272400, batch: "BATCH-0001109", expiry: "2029-07-15" } },
    { id: "SUP-001", name: "Nông nghiệp Xanh Việt", province: "Đắk Lắk", quality: 4.7, transactions: 81, spend: 5253013300, share: 12.617829, latest: { id: "INV-TX-0001871", date: "2026-07-16", farm: "Nông trại Cửu Long", warehouse: "Kho vật tư Nông trại Cửu Long", material: "Bộ bảo hộ lao động", quantity: 305.306, unit: "piece", unitCost: 420300, spend: 128334400, batch: "BATCH-0001871", expiry: "2029-07-15" } },
    { id: "SUP-003", name: "Sinh học Đông Nam", province: "Đồng Nai", quality: 4.6, transactions: 80, spend: 5219731600, share: 12.537885, latest: { id: "INV-TX-0001363", date: "2026-07-15", farm: "Nông trại Biển Hồ", warehouse: "Kho vật tư Nông trại Biển Hồ", material: "Cây giống cà phê", quantity: 3021.606, unit: "piece", unitCost: 23800, spend: 71897800, batch: "BATCH-0001363", expiry: "2026-11-12" } },
    { id: "SUP-006", name: "An toàn Nông trại", province: "Bình Dương", quality: 4.5, transactions: 82, spend: 5115092300, share: 12.28654, latest: { id: "INV-TX-0000881", date: "2026-07-16", farm: "Nông trại Miền Đông", warehouse: "Kho vật tư Nông trại Miền Đông", material: "Thuốc trừ nấm sinh học", quantity: 209, unit: "liter", unitCost: 128000, spend: 26752000, batch: "BATCH-0000881", expiry: "2026-08-02" } },
    { id: "SUP-002", name: "Vật tư Mekong", province: "Cần Thơ", quality: 4.5, transactions: 81, spend: 5075779000, share: 12.192109, latest: { id: "INV-TX-0001897", date: "2026-07-15", farm: "Nông trại Cửu Long", warehouse: "Kho vật tư Nông trại Cửu Long", material: "Bẫy côn trùng", quantity: 121, unit: "piece", unitCost: 38000, spend: 4598000, batch: "BATCH-0001897", expiry: "2026-08-02" } },
    { id: "SUP-004", name: "Giống cây Việt", province: "Lâm Đồng", quality: 4.4, transactions: 80, spend: 5072940000, share: 12.18529, latest: { id: "INV-TX-0000108", date: "2026-07-16", farm: "Nông trại Cao Nguyên", warehouse: "Kho vật tư Nông trại Cao Nguyên", material: "Thuốc trừ nấm sinh học", quantity: 209, unit: "liter", unitCost: 128000, spend: 26752000, batch: "BATCH-0000108", expiry: "2026-08-02" } },
    { id: "SUP-008", name: "Cơ khí Cao Nguyên", province: "Gia Lai", quality: 4.4, transactions: 81, spend: 5024409600, share: 12.068719, latest: { id: "INV-TX-0001643", date: "2026-07-16", farm: "Nông trại Cửu Long", warehouse: "Kho vật tư Nông trại Cửu Long", material: "Thuốc trừ nấm sinh học", quantity: 209, unit: "liter", unitCost: 128000, spend: 26752000, batch: "BATCH-0001643", expiry: "2026-08-02" } },
  ];

  const materialComparison = [
    { code: "MAT-NPK", name: "Phân NPK 16-16-8", category: "Phân bón", unit: "kg", transactions: 42, quantity: 315389.869, spend: 5934509000, average: 18816.422, share: 14.254793 },
    { code: "MAT-FUNGICIDE", name: "Thuốc trừ nấm sinh học", category: "Thuốc BVTV", unit: "liter", transactions: 45, quantity: 29384.226, spend: 3785737700, average: 128835.713, share: 9.093407 },
    { code: "MAT-INSECT", name: "Thuốc trừ sâu sinh học", category: "Thuốc BVTV", unit: "liter", transactions: 42, quantity: 24435.539, spend: 3523908200, average: 144212.419, share: 8.464488 },
    { code: "MAT-PPE", name: "Bộ bảo hộ lao động", category: "An toàn", unit: "piece", transactions: 45, quantity: 8018.017, spend: 3437265500, average: 428692.718, share: 8.256371 },
    { code: "MAT-UREA", name: "Phân urê", category: "Phân bón", unit: "kg", transactions: 42, quantity: 218323.936, spend: 3358752900, average: 15384.263, share: 8.067782 },
    { code: "MAT-SEED-RICE", name: "Hạt giống lúa", category: "Hạt giống", unit: "kg", transactions: 42, quantity: 92777.119, spend: 3214007900, average: 34642.247, share: 7.720103 },
    { code: "MAT-ORGANIC", name: "Phân hữu cơ", category: "Phân bón", unit: "kg", transactions: 45, quantity: 448977.222, spend: 3088662300, average: 6879.33, share: 7.41902 },
    { code: "MAT-SEED-VEG", name: "Hạt giống rau", category: "Hạt giống", unit: "kg", transactions: 42, quantity: 13926.18, spend: 3005926000, average: 215847.131, share: 7.220286 },
    { code: "MAT-DURIAN", name: "Cây giống sầu riêng", category: "Cây giống", unit: "piece", transactions: 45, quantity: 14081.973, spend: 2362338200, average: 167756.194, share: 5.674377 },
    { code: "MAT-FUEL", name: "Nhiên liệu máy nông nghiệp", category: "Nhiên liệu", unit: "liter", transactions: 42, quantity: 79915.012, spend: 1984433300, average: 24831.796, share: 4.766643 },
    { code: "MAT-COFFEE", name: "Cây giống cà phê", category: "Cây giống", unit: "piece", transactions: 45, quantity: 79039.653, spend: 1916459100, average: 24246.806, share: 4.603368 },
    { code: "MAT-FILTER", name: "Lõi lọc tưới", category: "Vật tư tưới", unit: "piece", transactions: 42, quantity: 6362.637, spend: 1881422800, average: 295698.592, share: 4.51921 },
    { code: "MAT-DRIP", name: "Dây tưới nhỏ giọt", category: "Vật tư tưới", unit: "piece", transactions: 42, quantity: 19161.026, spend: 1869836100, average: 97585.385, share: 4.491379 },
    { code: "MAT-PH", name: "Dung dịch cân bằng pH", category: "Xử lý đất", unit: "liter", transactions: 42, quantity: 20459.038, spend: 1578477100, average: 77153.046, share: 3.791529 },
    { code: "MAT-TRAP", name: "Bẫy côn trùng", category: "Kiểm soát dịch hại", unit: "piece", transactions: 45, quantity: 17700.028, spend: 689937800, average: 38979.475, share: 1.657243 },
  ];

  window.AGRI_COST_FIXTURE = {
    meta: { runId: "synthetic-2026-07-18-20260718", asOf: "2026-07-18", pipeline: "agriinsight-bronze-silver-gold-v1", scope: "Doanh nghiệp · 6 nông trại", role: "Finance analyst · enterprise read" },
    operating: {
      title: "Chi phí vận hành", eyebrow: "Operating cost · P&L", definition: "Chi phí vật tư và nhân công đã phân bổ cho hoạt động canh tác; không gồm dòng tiền mua hàng hay giá trị tồn kho.", defaultSelection: "bon-phan",
      summary: [
        { label: "Chi phí vận hành", value: 266980248000, kind: "vnd", note: "672 hoạt động · 48 mùa vụ" },
        { label: "So với ngân sách", value: -51629087000, kind: "signed-vnd", note: "thấp hơn ngân sách 318,61 tỷ ₫" },
        { label: "Lợi nhuận vận hành", value: 198789670000, kind: "vnd", note: "biên lợi nhuận 42,68%" },
        { label: "Đối soát", value: "48/48", kind: "text", note: "component delta bằng 0 ₫" },
      ],
      trend: { title: "Nhịp chi phí theo tháng", summary: "Đỉnh chi phí 25,70 tỷ ₫ vào 05/2026; doanh thu và lợi nhuận chỉ là ngữ cảnh P&L, không phải procurement.", rows: operatingMonthly },
      reconciliation: { title: "Đối soát thành phần", status: "48/48 mùa vụ cân bằng", note: "Vật tư + nhân công = chi phí vận hành; component delta 0 ₫.", rows: [{ label: "Vật tư vận hành", value: 135347938000, kind: "vnd" }, { label: "Nhân công vận hành", value: 131632310000, kind: "vnd" }, { label: "Tổng đã đối soát", value: 266980248000, kind: "vnd", emphasis: true }] },
      drivers: operatingDrivers, comparison: farmComparison,
      export: { lens: "Operating cost · P&L", scope: "Doanh nghiệp · 6 nông trại", contract: "cost_activity_detail · CSV UTF-8", estimatedRows: 672, columns: 18, cutoff: "2026-07-18" },
    },
    procurement: {
      title: "Chi tiêu mua hàng", eyebrow: "Procurement spend · non-P&L", definition: "Giá trị của 648 giao dịch nhập có nhà cung cấp; không cộng vào chi phí vận hành hoặc giá trị tồn kho.", defaultSelection: "SUP-005",
      summary: [
        { label: "Chi tiêu mua hàng", value: 41631673900, kind: "vnd", note: "648 giao dịch nhập" },
        { label: "Nhà cung cấp", value: 8, kind: "integer", note: "quality rating 4,2–4,7" },
        { label: "Kho nhận hàng", value: 6, kind: "integer", note: "6 nông trại doanh nghiệp" },
        { label: "Tháng phát sinh", value: 7, kind: "integer", note: "01/2025–07/2026" },
      ],
      trend: { title: "Nhịp mua hàng theo tháng", summary: "Đỉnh mua hàng 9,45 tỷ ₫ vào 01/2025; chỉ bảy tháng có phiếu nhập trong snapshot.", rows: procurementMonthly },
      reconciliation: { title: "Ranh giới hạch toán", status: "Không ghi nhận vào P&L", note: "Procurement là dòng tiền mua hàng; giá trị tồn kho có journey riêng tại Inventory.", rows: [{ label: "Procurement spend", value: 41631673900, kind: "vnd" }, { label: "Operating cost", value: "Loại trừ", kind: "text" }, { label: "Inventory value", value: "Mở tại Inventory", kind: "text", emphasis: true }] },
      drivers: supplierDrivers, comparison: materialComparison,
      export: { lens: "Procurement spend · non-P&L", scope: "Doanh nghiệp · 6 nông trại", contract: "procurement_detail · CSV UTF-8", estimatedRows: 648, columns: 21, cutoff: "2026-07-18" },
    },
  };
})();
