package com.example.model

import android.util.Log

object AdventureCatalog {

    private const val TAG = "AdventureCatalog"

    internal var missingIdObserver: ((id: String) -> Unit)? = null

    private fun logMissingAdventure(id: String) {
        missingIdObserver?.invoke(id)
        val msg = "Invalid or missing adventure ID requested: '$id'"
        try {
            Log.e(TAG, msg)
        } catch (_: RuntimeException) {
            System.err.println("[$TAG] $msg")
        }
    }

    val adventures: List<AdventureConfig> = listOf(
        AdventureConfig(
            id = "adv_demacia_outskirts",
            name = "Vùng Ngoại Ô Demacia",
            region = Region.DEMACIA,
            starRating = 0.5f,
            difficultyLabel = "0.5★ Cơ Bản",
            description = "Tuần tra vùng biên giới đá bạc của Demacia và thách thức Đội Tiên Phong Bất Khuất.",
            bossName = "Garen, Sức Mạnh Demacia",
            rewardXp = 250,
            rewardFragments = 15,
            mutators = emptyList(),
            mapNodes = listOf(
                MapNode(
                    id = "d_start", act = 1, row = 0, column = 1, type = NodeType.START,
                    title = "Cổng Biên Giới", subtitle = "Khởi đầu hành trình",
                    nextNodeIds = listOf("d_power_1")
                ),
                MapNode(
                    id = "d_power_1", act = 1, row = 1, column = 1, type = NodeType.POWER_NODE,
                    title = "Đền Thờ Quyết Tâm", subtitle = "Chọn 1 trong 3 Sức Mạnh",
                    nextNodeIds = listOf("d_fight_1a", "d_fight_1b")
                ),
                MapNode(
                    id = "d_fight_1a", act = 1, row = 2, column = 0, type = NodeType.COMBAT,
                    title = "Trinh Sát Tiên Phong", subtitle = "Giao Chiến Tiêu Chuẩn",
                    enemyName = "Trung Đội Trinh Sát", enemyPowerTitle = "Sẵn Sàng Đội Hình",
                    enemyPowerDescription = "Bài quân địch nhận +1|+0 khi tấn công.",
                    enemyMaxHp = 15, enemyStartingMana = 1,
                    enemyDeckCardIds = listOf("card_cithria", "card_brightsteel_protector", "card_greenfang_warden", "card_single_combat"),
                    goldReward = 70, nextNodeIds = listOf("d_reinf_1")
                ),
                MapNode(
                    id = "d_fight_1b", act = 1, row = 2, column = 2, type = NodeType.COMBAT,
                    title = "Tuần Tra Đá Kháng Ma", subtitle = "Giao Chiến Tiêu Chuẩn",
                    enemyName = "Vệ Binh Thạch Kháng Ma", enemyPowerTitle = "Phòng Hộ Đá",
                    enemyPowerDescription = "Kẻ địch khởi đầu với +5 Máu Tối Đa.",
                    enemyMaxHp = 18, enemyStartingMana = 1,
                    enemyDeckCardIds = listOf("card_cithria", "card_petricite_broadwing", "card_laurent_protege", "card_prismatic_barrier"),
                    goldReward = 75, nextNodeIds = listOf("d_shop_1")
                ),
                MapNode(
                    id = "d_reinf_1", act = 1, row = 3, column = 0, type = NodeType.REINFORCEMENT_NODE,
                    title = "Hiệu Triệu Đồng Minh", subtitle = "Chọn Tướng Tiếp Viện",
                    nextNodeIds = listOf("d_midboss")
                ),
                MapNode(
                    id = "d_shop_1", act = 1, row = 3, column = 2, type = NodeType.SHOP_NODE,
                    title = "Thương Nhân Lang Thang", subtitle = "Mua Bài & Trang Bị",
                    nextNodeIds = listOf("d_midboss")
                ),
                MapNode(
                    id = "d_midboss", act = 1, row = 4, column = 1, type = NodeType.MID_BOSS,
                    title = "Luxanna Crownguard", subtitle = "Trùm Giữa Chặng 1",
                    enemyName = "Lux (Trùm Phụ)", enemyPowerTitle = "Khiên Lăng Kính",
                    enemyPowerDescription = "Đầu Vòng Đấu: Bài quân địch mạnh nhất nhận Lá Chắn.",
                    enemyMaxHp = 22, enemyStartingMana = 2,
                    enemyDeckCardIds = listOf("card_lux", "card_brightsteel_protector", "card_cithria", "card_prismatic_barrier", "card_golden_aegis"),
                    goldReward = 110, nextNodeIds = listOf("d_heal_2", "d_chest_2")
                ),
                MapNode(
                    id = "d_heal_2", act = 2, row = 5, column = 0, type = NodeType.HEALER_NODE,
                    title = "Trạm Dừng Cánh Bạc", subtitle = "Hồi Máu hoặc Xóa Bài",
                    nextNodeIds = listOf("d_boss")
                ),
                MapNode(
                    id = "d_chest_2", act = 2, row = 5, column = 2, type = NodeType.ITEM_CHEST,
                    title = "Kho Vũ Khí", subtitle = "Gắn Trang Bị Vào Bài",
                    nextNodeIds = listOf("d_boss")
                ),
                MapNode(
                    id = "d_boss", act = 2, row = 6, column = 1, type = NodeType.FINAL_BOSS,
                    title = "Garen, Sức Mạnh Demacia", subtitle = "Trùm Cuối",
                    enemyName = "Garen", enemyPowerTitle = "Vì Demacia!",
                    enemyPowerDescription = "Địch khởi đầu 2 Năng Lượng. Mỗi 2 vòng, toàn bộ quân địch nhận +1|+1.",
                    enemyMaxHp = 28, enemyStartingMana = 2,
                    enemyDeckCardIds = listOf("card_garen", "card_cithria", "card_laurent_protege", "card_radiant_guardian", "card_single_combat", "card_golden_aegis"),
                    goldReward = 200, nextNodeIds = emptyList()
                )
            )
        ),
        AdventureConfig(
            id = "adv_ionia_woods",
            name = "Rừng Linh Hồn Ionia",
            region = Region.IONIA,
            starRating = 1.5f,
            difficultyLabel = "1.5★ Tiêu Chuẩn",
            description = "Băng qua khu rừng hoa linh thiêng được canh giữ bởi sát thủ bóng tối và kiếm sư cổ xưa.",
            bossName = "Karma, Kẻ Được Khai Sáng",
            rewardXp = 450,
            rewardFragments = 20,
            mutators = listOf(
                GlobalMutator("mut_spirit_breeze", "Gió Linh Hồn", "Đầu Vòng Đấu: Cả hai bên nhận 1 Năng Lượng Phép.")
            ),
            mapNodes = listOf(
                MapNode(
                    id = "i_start", act = 1, row = 0, column = 1, type = NodeType.START,
                    title = "Đường Mòn Hoa Đào", subtitle = "Tiến vào Rừng Linh Hồn",
                    nextNodeIds = listOf("i_power_1")
                ),
                MapNode(
                    id = "i_power_1", act = 1, row = 1, column = 1, type = NodeType.POWER_NODE,
                    title = "Tế Đàn Tâm Linh", subtitle = "Chọn 1 trong 3 Sức Mạnh",
                    nextNodeIds = listOf("i_fight_1a", "i_fight_1b")
                ),
                MapNode(
                    id = "i_fight_1a", act = 1, row = 2, column = 0, type = NodeType.COMBAT,
                    title = "Môn Đồ Bóng Đêm", subtitle = "Giao Tranh Lẩn Khuất",
                    enemyName = "Ám Ảnh Kinkou", enemyPowerTitle = "Lối Đi Vô Hình",
                    enemyPowerDescription = "Bài quân Lẩn Khuất của địch có +1|+0.",
                    enemyMaxHp = 20, enemyStartingMana = 2,
                    enemyDeckCardIds = listOf("card_shadow_assassin", "card_kinkou_student", "card_sumpworks_mapper", "card_steel_tempest"),
                    goldReward = 80, nextNodeIds = listOf("i_event_1")
                ),
                MapNode(
                    id = "i_fight_1b", act = 1, row = 2, column = 2, type = NodeType.ELITE_COMBAT,
                    title = "Võ Đường Kiếm Vũ", subtitle = "Chạm Trán Tinh Anh",
                    enemyName = "Kiếm Sư Tỷ Thí", enemyPowerTitle = "Huấn Luyện Cuồng Phong",
                    enemyPowerDescription = "Đầu Vòng Đấu: Địch làm Choáng bài quân yếu nhất của bạn mỗi 2 vòng.",
                    enemyMaxHp = 24, enemyStartingMana = 2,
                    enemyDeckCardIds = listOf("card_fae_blader", "card_shadow_assassin", "card_steel_tempest", "card_concussive_palm"),
                    goldReward = 110, nextNodeIds = listOf("i_reinf_1")
                ),
                MapNode(
                    id = "i_event_1", act = 1, row = 3, column = 0, type = NodeType.NARRATIVE_EVENT,
                    title = "Miếu Thì Thầm", subtitle = "Lựa Chọn Rủi Ro & Phần Thưởng",
                    nextNodeIds = listOf("i_midboss")
                ),
                MapNode(
                    id = "i_reinf_1", act = 1, row = 3, column = 2, type = NodeType.REINFORCEMENT_NODE,
                    title = "Lãng Khách Đồng Hành", subtitle = "Chọn Tướng Tiếp Viện",
                    nextNodeIds = listOf("i_midboss")
                ),
                MapNode(
                    id = "i_midboss", act = 1, row = 4, column = 1, type = NodeType.MID_BOSS,
                    title = "Zed, Chúa Tể Bóng Tối", subtitle = "Trùm Giữa Chặng 1",
                    enemyName = "Zed", enemyPowerTitle = "Phân Thân Bóng Tối",
                    enemyPowerDescription = "Bài quân tấn công của địch nhận +1|+1 khi ra đòn.",
                    enemyMaxHp = 28, enemyStartingMana = 2,
                    enemyDeckCardIds = listOf("card_zed", "card_shadow_assassin", "card_spectral_ironhound", "card_steel_tempest"),
                    goldReward = 130, nextNodeIds = listOf("i_shop_2", "i_heal_2")
                ),
                MapNode(
                    id = "i_shop_2", act = 2, row = 5, column = 0, type = NodeType.SHOP_NODE,
                    title = "Thương Nhân Linh Giới", subtitle = "Mua Bài, Trang Bị & Sức Mạnh",
                    nextNodeIds = listOf("i_boss")
                ),
                MapNode(
                    id = "i_heal_2", act = 2, row = 5, column = 2, type = NodeType.HEALER_NODE,
                    title = "Suối Nguồn Tĩnh Lặng", subtitle = "Hồi Máu hoặc Xóa Bài",
                    nextNodeIds = listOf("i_boss")
                ),
                MapNode(
                    id = "i_boss", act = 2, row = 6, column = 1, type = NodeType.FINAL_BOSS,
                    title = "Karma, Kẻ Được Khai Sáng", subtitle = "Trùm Cuối",
                    enemyName = "Karma", enemyPowerTitle = "Hòa Hợp Khai Sáng",
                    enemyPowerDescription = "Địch khởi đầu 3 Năng Lượng và hồi 1 Máu Nhà Chính mỗi khi dùng phép.",
                    enemyMaxHp = 35, enemyStartingMana = 3,
                    enemyDeckCardIds = listOf("card_karma", "card_kinkou_student", "card_shadow_assassin", "card_concussive_palm", "card_prismatic_barrier"),
                    goldReward = 250, nextNodeIds = emptyList()
                )
            )
        ),
        AdventureConfig(
            id = "adv_zaun_foundry",
            name = "Xưởng Đúc Piltover & Zaun",
            region = Region.PILTOVER_ZAUN,
            starRating = 3.0f,
            difficultyLabel = "3.0★ Tinh Anh",
            description = "Đột nhập nhà máy cơ khí của Viktor nơi đầy rẫy người máy Nâng Cấp và bẫy Nấm Độc.",
            bossName = "Viktor, Sứ Giả Máy Móc",
            rewardXp = 750,
            rewardFragments = 25,
            mutators = listOf(
                GlobalMutator("mut_glorious_assembly", "Dây Chuyền Huy Hoàng", "Quân của cả hai bên nhận +1|+0 mỗi khi dùng lá bài được tạo ra hoặc bài phép.")
            ),
            mapNodes = listOf(
                MapNode(
                    id = "z_start", act = 1, row = 0, column = 1, type = NodeType.START,
                    title = "Thang Máy Hố Sâu", subtitle = "Tiến xuống lòng đất Zaun",
                    nextNodeIds = listOf("z_power_1")
                ),
                MapNode(
                    id = "z_power_1", act = 1, row = 1, column = 1, type = NodeType.POWER_NODE,
                    title = "Lõi Hextech", subtitle = "Chọn 1 trong 3 Sức Mạnh",
                    nextNodeIds = listOf("z_fight_1a", "z_fight_1b")
                ),
                MapNode(
                    id = "z_fight_1a", act = 1, row = 2, column = 0, type = NodeType.COMBAT,
                    title = "Băng Hóa Chất", subtitle = "Sát Thương & Nấm Độc",
                    enemyName = "Băng Đội Bom", enemyPowerTitle = "Đạn Dược Bất Ổn",
                    enemyPowerDescription = "Bài quân địch nhận Tác Động 1.",
                    enemyMaxHp = 26, enemyStartingMana = 2,
                    enemyDeckCardIds = listOf("card_boomcrew_rookie", "card_zaunite_urchin", "card_mystic_shot", "card_teemo_puffcaps"),
                    goldReward = 95, nextNodeIds = listOf("z_chest_1")
                ),
                MapNode(
                    id = "z_fight_1b", act = 1, row = 2, column = 2, type = NodeType.ELITE_COMBAT,
                    title = "Đội Tuần Tra Cơ Khí", subtitle = "Giao Tranh Tinh Anh",
                    enemyName = "Phi Đội Đạn Đạo", enemyPowerTitle = "Nâng Cấp Hextech",
                    enemyPowerDescription = "Kẻ địch khởi đầu với 3 Năng Lượng.",
                    enemyMaxHp = 30, enemyStartingMana = 3,
                    enemyDeckCardIds = listOf("card_ballistic_bot", "card_void_abomination", "card_get_excited", "card_ruthless_raider"),
                    goldReward = 130, nextNodeIds = listOf("z_shop_1")
                ),
                MapNode(
                    id = "z_chest_1", act = 1, row = 3, column = 0, type = NodeType.ITEM_CHEST,
                    title = "Tủ Đồ Nhà Phát Minh", subtitle = "Gắn Trang Bị Vào Bài",
                    nextNodeIds = listOf("z_midboss")
                ),
                MapNode(
                    id = "z_shop_1", act = 1, row = 3, column = 2, type = NodeType.SHOP_NODE,
                    title = "Chợ Trời Hóa Chủ", subtitle = "Mua Bài & Trang Bị",
                    nextNodeIds = listOf("z_midboss")
                ),
                MapNode(
                    id = "z_midboss", act = 1, row = 4, column = 1, type = NodeType.MID_BOSS,
                    title = "Jinx, Khẩu Pháo Nổi Loạn", subtitle = "Trùm Giữa Chặng 1",
                    enemyName = "Jinx (Đối Thủ)", enemyPowerTitle = "Mưa Tên Lửa",
                    enemyPowerDescription = "Đầu Vòng Đấu: Gây 1 sát thương lên Nhà Chính của bạn.",
                    enemyMaxHp = 34, enemyStartingMana = 3,
                    enemyDeckCardIds = listOf("card_jinx", "card_boomcrew_rookie", "card_zaunite_urchin", "card_get_excited", "card_mystic_shot"),
                    goldReward = 160, nextNodeIds = listOf("z_reinf_2", "z_heal_2")
                ),
                MapNode(
                    id = "z_reinf_2", act = 2, row = 5, column = 0, type = NodeType.REINFORCEMENT_NODE,
                    title = "Cảnh Vệ Piltover", subtitle = "Chọn Tướng Tiếp Viện",
                    nextNodeIds = listOf("z_boss")
                ),
                MapNode(
                    id = "z_heal_2", act = 2, row = 5, column = 2, type = NodeType.HEALER_NODE,
                    title = "Trạm Y Tế Dã Chiến", subtitle = "Hồi Máu hoặc Xóa Bài",
                    nextNodeIds = listOf("z_boss")
                ),
                MapNode(
                    id = "z_boss", act = 2, row = 6, column = 1, type = NodeType.FINAL_BOSS,
                    title = "Viktor, Sứ Giả Máy Móc", subtitle = "Trùm Cuối",
                    enemyName = "Viktor", enemyPowerTitle = "Tiến Hóa Huy Hoàng",
                    enemyPowerDescription = "Địch khởi đầu 3 Năng Lượng. Bài quân địch nhận +1|+1 và từ khóa ngẫu nhiên!",
                    enemyMaxHp = 45, enemyStartingMana = 3,
                    enemyDeckCardIds = listOf("card_viktor", "card_ballistic_bot", "card_void_abomination", "card_rekindled_knight", "card_get_excited"),
                    goldReward = 320, nextNodeIds = emptyList()
                )
            )
        ),
        AdventureConfig(
            id = "adv_targon_nightmare",
            name = "Ác Mộng Đỉnh Targon",
            region = Region.TARGON,
            starRating = 4.5f,
            difficultyLabel = "4.5★ Ác Mộng",
            description = "Chinh phục đỉnh trời đầy thực thể vũ trụ, Lời Nguyền và Aurelion Sol.",
            bossName = "Aurelion Sol, Ác Long Thượng Giới",
            rewardXp = 1200,
            rewardFragments = 40,
            mutators = listOf(
                GlobalMutator("mut_celestial_might", "Hiểm Họa Vũ Trụ", "Kẻ địch khởi đầu với 4 Năng Lượng!"),
                GlobalMutator("mut_nightmare_curse", "Dư Âm Ác Mộng", "Trùm Cuối yểm Lời Nguyền Áp Chế lên tay bạn.")
            ),
            mapNodes = listOf(
                MapNode(
                    id = "t_start", act = 1, row = 0, column = 1, type = NodeType.START,
                    title = "Chân Núi Targon", subtitle = "Bắt đầu hành trình Ác Mộng",
                    nextNodeIds = listOf("t_power_1")
                ),
                MapNode(
                    id = "t_power_1", act = 1, row = 1, column = 1, type = NodeType.POWER_NODE,
                    title = "Mạch Năng Lượng Thiên Giới", subtitle = "Chọn Sức Mạnh Cao Cấp",
                    nextNodeIds = listOf("t_fight_1a", "t_fight_1b")
                ),
                MapNode(
                    id = "t_fight_1a", act = 1, row = 2, column = 0, type = NodeType.ELITE_COMBAT,
                    title = "Chiến Binh Solari", subtitle = "Đột Kích Hừng Đông",
                    enemyName = "Tiên Phong Solari", enemyPowerTitle = "Bình Minh Rực Cháy",
                    enemyPowerDescription = "Địch khởi đầu 4 Năng Lượng. Bài quân địch có +1|+1.",
                    enemyMaxHp = 35, enemyStartingMana = 4,
                    enemyDeckCardIds = listOf("card_solari_soldier", "card_radiant_guardian", "card_petricite_broadwing", "card_golden_aegis"),
                    goldReward = 130, nextNodeIds = listOf("t_event_1")
                ),
                MapNode(
                    id = "t_fight_1b", act = 1, row = 2, column = 2, type = NodeType.ELITE_COMBAT,
                    title = "Oan Hồn Bóng Tối", subtitle = "Bất Tử & Đáng Sợ",
                    enemyName = "Quân Đoàn Bóng Ma", enemyPowerTitle = "Nỗi Kinh Hoàng Bất Tử",
                    enemyPowerDescription = "Địch khởi đầu 4 Năng Lượng kèm cộng dồn Thiêng Liêng.",
                    enemyMaxHp = 35, enemyStartingMana = 4,
                    enemyDeckCardIds = listOf("card_rekindled_knight", "card_spectral_ironhound", "card_void_abomination", "card_flash_freeze"),
                    goldReward = 135, nextNodeIds = listOf("t_shop_1")
                ),
                MapNode(
                    id = "t_event_1", act = 1, row = 3, column = 0, type = NodeType.NARRATIVE_EVENT,
                    title = "Tế Đàn Tinh Tú", subtitle = "Hiến Tế Máu Đổi Sức Mạnh Sử Thi",
                    nextNodeIds = listOf("t_midboss")
                ),
                MapNode(
                    id = "t_shop_1", act = 1, row = 3, column = 2, type = NodeType.SHOP_NODE,
                    title = "Thương Nhân Tinh Tú", subtitle = "Nâng Cấp Cao Cấp",
                    nextNodeIds = listOf("t_midboss")
                ),
                MapNode(
                    id = "t_midboss", act = 1, row = 4, column = 1, type = NodeType.MID_BOSS,
                    title = "Ashe, Nữ Hoàng Băng Giá", subtitle = "Trùm Giữa Chặng 1",
                    enemyName = "Ashe (Ác Mộng)", enemyPowerTitle = "Mùa Đông Vô Tận",
                    enemyPowerDescription = "Địch khởi đầu 4 Năng Lượng. Đầu Vòng Đấu: Đóng Băng đồng minh mạnh nhất.",
                    enemyMaxHp = 42, enemyStartingMana = 4,
                    enemyDeckCardIds = listOf("card_ashe", "card_icevale_archer", "card_ruthless_raider", "card_flash_freeze"),
                    goldReward = 190, nextNodeIds = listOf("t_power_2", "t_heal_2")
                ),
                MapNode(
                    id = "t_power_2", act = 2, row = 5, column = 0, type = NodeType.POWER_NODE,
                    title = "Phúc Lành Tinh Vân", subtitle = "Chọn Sức Mạnh Bổ Sung",
                    nextNodeIds = listOf("t_boss")
                ),
                MapNode(
                    id = "t_heal_2", act = 2, row = 5, column = 2, type = NodeType.HEALER_NODE,
                    title = "Thánh Điện Mặt Trăng", subtitle = "Hồi Máu hoặc Xóa Bài",
                    nextNodeIds = listOf("t_boss")
                ),
                MapNode(
                    id = "t_boss", act = 2, row = 6, column = 1, type = NodeType.FINAL_BOSS,
                    title = "Aurelion Sol", subtitle = "Trùm Cuối Ác Mộng",
                    enemyName = "Aurelion Sol", enemyPowerTitle = "Đấng Sáng Tạo Sao",
                    enemyPowerDescription = "Địch khởi đầu 4 Năng Lượng. Đầu Vòng Đấu: Quân địch nhận +1|+1 & yểm Áp Chế.",
                    enemyMaxHp = 60, enemyStartingMana = 4,
                    enemyDeckCardIds = listOf("card_aurelion_sol", "card_rekindled_knight", "card_void_abomination", "card_radiant_guardian", "card_golden_aegis"),
                    goldReward = 500, nextNodeIds = emptyList()
                )
            )
        )
    )

    fun findAdventureOrNull(id: String): AdventureConfig? = adventures.find { it.id == id }

    fun getAdventure(id: String): AdventureConfig =
        adventures.find { it.id == id } ?: run {
            logMissingAdventure(id)
            adventures.first()
        }
}
