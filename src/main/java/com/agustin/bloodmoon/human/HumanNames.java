package com.agustin.bloodmoon.human;

/** Nombres por cultura y sexo, deterministas a partir de la semilla del humano. */
public final class HumanNames {
    private HumanNames() {}

    private static final String[] PLAINS_M = {
            "Aldric", "Bertram", "Cedric", "Duncan", "Edmund", "Florian", "Gareth", "Godfrey", "Hugo", "Ivo",
            "Jasper", "Konrad", "Leopold", "Lothar", "Martin", "Nolan", "Osric", "Percival", "Roland", "Sigmund",
            "Tobias", "Ulric", "Walter", "Wendel", "Anselm", "Baldwin", "Corwin", "Emeric", "Gunther", "Hector",
            "Matthias", "Otto", "Rupert", "Tristan", "Alban", "Brom", "Elias", "Joren", "Piers", "Robin"};
    private static final String[] PLAINS_F = {
            "Adela", "Beatrix", "Clarice", "Della", "Edith", "Frieda", "Gisela", "Hilda", "Isolde", "Jocelyn",
            "Katrin", "Linnea", "Matilda", "Nell", "Odile", "Petra", "Rosalind", "Sabine", "Tilda", "Ursula",
            "Wilma", "Agnes", "Brenna", "Cecily", "Elke", "Greta", "Helena", "Ingrid", "Lisbet", "Marlene",
            "Orla", "Rowena", "Sybil", "Ylva", "Alma", "Elsbeth", "Hedda", "Mira", "Runa", "Wren"};
    private static final String[] PLAINS_S = {
            "Ashford", "Brook", "Carter", "Dunmore", "Elwood", "Fairley", "Greaves", "Hollow", "Kestrel", "Lindqvist",
            "Marsh", "Norwood", "Oakes", "Pennick", "Redfern", "Stone", "Thatcher", "Underhill", "Vale", "Whitlock",
            "Barrow", "Coldwell", "Fenwick", "Hart", "Millward", "Rook", "Sedge", "Tanner", "Weller", "Yarrow"};

    private static final String[] DESERT_M = {
            "Amir", "Bashir", "Darius", "Faris", "Ghazi", "Hakim", "Idris", "Jamal", "Kamran", "Latif",
            "Malik", "Nadir", "Omar", "Qasim", "Rashid", "Samir", "Tariq", "Yusuf", "Zahir", "Anwar",
            "Basim", "Cyrus", "Fadil", "Harun", "Imran", "Karim", "Mansur", "Nasser", "Rafiq", "Selim"};
    private static final String[] DESERT_F = {
            "Amira", "Basma", "Dalia", "Farah", "Ghalia", "Hana", "Inaya", "Jamila", "Karima", "Layla",
            "Mariam", "Nadia", "Rania", "Salma", "Samira", "Yasmin", "Zahra", "Aziza", "Leila", "Nour",
            "Shirin", "Soraya", "Tahira", "Yara", "Zaynab", "Anisa", "Dunya", "Halima", "Malika", "Rasha"};
    private static final String[] DESERT_S = {
            "al-Rashid", "ibn Tarek", "al-Qadir", "ibn Samir", "al-Hamra", "ibn Yusuf", "al-Sahra", "ibn Harun",
            "al-Nur", "ibn Karim", "al-Zahir", "ibn Malik", "al-Wadi", "ibn Faris", "al-Baqi", "ibn Omar"};

    public static String name(long seed, Culture culture, boolean female) {
        long h = seed * 0x9E3779B97F4A7C15L + 0x632BE59BD9B4E019L;
        h ^= h >>> 29;
        int i = (int) ((h & 0x7fffffff) % 1009);
        int j = (int) (((h >>> 32) & 0x7fffffff) % 1013);
        String[] first, last;
        if (culture == Culture.DESERT) {
            first = female ? DESERT_F : DESERT_M;
            last = DESERT_S;
        } else {
            first = female ? PLAINS_F : PLAINS_M;
            last = PLAINS_S;
        }
        return first[i % first.length] + " " + last[j % last.length];
    }
}
