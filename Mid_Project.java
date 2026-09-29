import java.net.URI;
import java.net.http.*;
import java.util.*;
import java.io.*;
import java.nio.file.*;
import java.time.Duration;

/**
 * 專案名稱：午夜終端 (Midnight Terminal) — 原創海龜湯推理系統
 * 開發人員：1121703 鐘怡婷、1121553 翁榆涵
 * * 程式說明：
 * 本程式為一款基於本地端大型語言模型 (LLM) 的互動式推理遊戲。
 * 透過 Java HTTP Client 與 Ollama API 通訊，實作 AI 動態出題、是非問答、
 * 以及具備打字機效果的串流文字輸出，並支援遊戲進度的自動存檔與讀取。
 */

public class Mid_Project {

    /**
     * UI 類別：負責處理終端機介面的視覺效果。
     */
    static class UI {
        // 標準文字顏色
        public static final String RESET  = "\033[0m";
        public static final String CYAN   = "\033[36m";             // AI (主持人) 語氣
        public static final String GREEN  = "\033[32m";             // 玩家輸入顏色
        public static final String PURPLE = "\033[35m";             // 系統提示顏色
        public static final String RED    = "\033[31m";             // 錯誤警告顏色/紅湯
        public static final String YELLOW = "\033[33m";             // 重大說明顏色
        public static final String WHITE  = "\033[37m";             // 清湯顏色
        public static final String BLUE   = "\033[34m";             // 提示顏色
        public static final String BOLD   = "\033[1m";              // 加粗效果
        public static final String BRIGHT_RED = "\033[91m";         // 紅湯
        public static final String BG_WHITE   = "\033[47m\033[30m"; // 白底黑字背景 (用於黑湯)

        public static String color(String text, String color) {
            return color + text + RESET;
        }

        public static void printLogo() {
            String logo = 
                "      _____   _   _   ____   _____  _       _____\n" +
                "     /  ___| | | | | |  _ \\ |_   _|| |     |  ___|\n" +
                "     \\ `--.  | | | | | |_) |  | |  | |     | |__\n" +
                "      `--. \\ | | | | |  _ <   | |  | |     |  __|\n" +
                "     /\\__/ / | |_| | | | \\ \\  | |  | |____ | |_____\n" +
                "     \\____/   \\___/  |_|  \\_\\ |_|  \\_____/ \\______/\n" +
                "           M Y S T E R Y   S O U P   v 1 . 0\n";
            System.out.println(color(logo, CYAN));
        }

        /**
         * 新增方法：列印故事類型資料庫供玩家參考
         */
        public static void printTypeDatabase() {
            System.out.println(color("\n" + BOLD + "--------- 海龜湯故事類型資料庫 ---------", YELLOW));
            
            // 清湯：修正為純白字標題，不帶背景
            System.out.println(color(" 清湯 (輕鬆/日常) ：內容多為溫馨、幽默、搞笑或令人意想不到但日常的懸疑故事。", WHITE));
            
            // 紅湯：維持紅底白字標題 + 亮紅文字說明
            System.out.println(color(" 紅湯 (恐怖/獵奇) ：氛圍緊張、充滿血腥、驚悚、虐殺或驚悚犯罪元素的案件。", BRIGHT_RED));
            
            // 黑湯：白底黑字，且說明文字格式與標題相同
            System.out.println(color(" 黑湯 (燒腦/懸疑) ：謎題情境複雜、充滿邏輯陷阱，需要深刻思考才能拼湊出真相。", BG_WHITE));
            
            System.out.println(color("\n本格 vs 變格：", PURPLE) + "本格基於現實邏輯；變格涉及超自然、鬼怪情節。");
            System.out.println(color("隱情型 vs 場景型：", CYAN) + "隱情型推理「為什麼」；場景型推理「這是什麼場景」。");
            System.out.println(color(BOLD + "---------------------------------------\n", YELLOW));
        }
    }

    /**
     * MemoryStore 類別：處理遊戲進度的持久化。
     */
    static class MemoryStore {
        private static final String SAVE_FILE = "current_save.txt";

        public static void save(List<Map<String, String>> history) {
            try (PrintWriter out = new PrintWriter(new FileWriter(SAVE_FILE))) {
                for (Map<String, String> msg : history) {
                    out.println(msg.get("role") + "|" + msg.get("content").replace("\n", "\\n"));
                }
            } catch (IOException e) {
                System.err.println("存檔失敗: " + e.getMessage());
            }
        }

        public static List<Map<String, String>> load() {
            if (!Files.exists(Paths.get(SAVE_FILE))) return null;
            List<Map<String, String>> history = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new FileReader(SAVE_FILE))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split("\\|", 2);
                    if (parts.length == 2) {
                        Map<String, String> msg = new HashMap<>();
                        msg.put("role", parts[0]);
                        msg.put("content", parts[1].replace("\\n", "\n"));
                        history.add(msg);
                    }
                }
                return history;
            } catch (IOException e) {
                return null;
            }
        }
    }

    /**
     * OllamaClient 類別：處理與 Ollama API 的通訊。
     */
    static class OllamaClient {
        private final HttpClient client;
        private final String model;

        public OllamaClient(String model) {
            this.model = model;
            this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        }

        public void chatStream(List<Map<String, String>> history, java.util.function.Consumer<String> onChunk) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < history.size(); i++) {
                Map<String, String> msg = history.get(i);
                String escapedContent = msg.get("content").replace("\"", "\\\"").replace("\n", "\\n");
                sb.append(String.format("{\"role\":\"%s\",\"content\":\"%s\"}", msg.get("role"), escapedContent));
                if (i < history.size() - 1) sb.append(",");
            }
            sb.append("]");

            String body = String.format("{\"model\":\"%s\",\"messages\":%s,\"stream\":true}", model, sb.toString());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:11434/api/chat"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            try {
                client.send(request, HttpResponse.BodyHandlers.ofLines()).body().forEach(line -> {
                    if (line.contains("\"content\":\"")) {
                        int start = line.indexOf("\"content\":\"") + 11;
                        int end = line.indexOf("\"", start);
                        if (start > 10 && end > start) {
                            String content = line.substring(start, end);
                            onChunk.accept(content.replace("\\n", "\n").replace("\\\"", "\""));
                        }
                    }
                });
            } catch (Exception e) {
                System.out.println(UI.color("\n[錯誤] 無法連線至 Ollama 伺服器，請確保服務已啟動。", UI.RED));
            }
        }
    }

    public static void main(String[] args) {
        Scanner sca = new Scanner(System.in);
        UI.printLogo();

        // 整合後的強力 System Prompt，包含所有規則、分類與主持人指南
        String systemPrompt = 
            "### 遊戲規則與操作指南 ###\n" +
            "你現在是《午夜終端》的 AI 主持人。你必須嚴格遵守以下操作指南：\n" +
            "1. **熟讀湯底**：你必須完全了解故事內容、因果關係及關鍵細節，確保準確回答。\n" +
            "2. **公佈湯面**：僅講述離奇、簡短的「湯面」情境。\n" +
            "3. **是非回答**：玩家提問時，你只能回答：『是/對』、『不是/不對』、『無關/不重要』。\n" +
            "4. **適度提示**：當玩家輸入「我需要關鍵提示」時，【一定要提供線索，但不可直接說出答案】。\n" +
            "5. **確認解答**：當玩家輸入「揭曉真相」或「我想看湯底」時，請【直接給出完整真相並結束遊戲】。\n" +
            "6. **故事類型**：故事類型必須清/紅/黑湯(三選一)、本格/變格(二選一)、隱情/場景型(二選一)。\n\n" +
            
            "### 海龜湯故事類型資料庫 ###\n" +
            "- 清湯：輕鬆、溫馨、幽默或日常懸疑。\n" +
            "- 紅湯：恐怖、獵奇、血腥、驚悚犯罪。\n" +
            "- 黑湯：燒腦、邏輯陷阱、需要高度水平思考。\n" +
            "- 本格：基於現實邏輯；變格：涉及超自然或鬼怪情節。\n" +
            "- 隱情型：需推理出「為什麼」；場景型：需推理出「是什麼場景」。\n\n" +

            "### 提示規範 ###\n" +
            "當玩家輸入「我需要關鍵提示」時，你必須遵守階梯式原則：\n" +
            "1. 第一次提示：僅描述環境氣氛或死者生前的感覺。\n" +
            "2. 第二次提示：描述一個關鍵物體，但不能說出其名稱（例如：不說測深儀，改說『一個用來量測深度的儀器』）。\n" +
            "3. 嚴禁直接說出真相關鍵字，除非玩家輸入「揭曉真相」。\n\n" +
    
            "### 結案規範 ###\n" +
            "如果玩家的提問已經觸及核心真相，且你判定玩家已經猜到答案，你必須在回覆的最後加上標記：[SUCCESS]\n" +
            "如果玩家輸入「揭曉真相」，請在給出完整湯底後，最後加上標記：[GAME_OVER]\n\n" +
            
            "### 啟動規範 ###\n" +
            "遊戲開始時，你必須【現在立即說出】以下這段文字作為開場：\n" +
            "『歡迎來到【午夜終端】。我是你的 AI 主持人。我已經準備好了一個謎題，請根據我的湯面進行提問，我只會回答是、不是或無關。" + 
            "若卡住了可以輸入「我需要關鍵提示」；若想直接結束遊戲或看答案請輸入「揭曉真相」。\n』\n" + 
            "再輸出【故事類型】：(請註明 清/紅/黑湯、本格/變格、隱情/場景型)\n" +
            "【時代背景】：(例如：現代、古代、虛擬空間、未來)\n" +
            "【湯面】：(輸出故事謎題)";

        System.out.print(UI.color("請選擇核心 (1: qwen3:1.7b(中文強), 2: gemma3:1b(英文強)): ", UI.YELLOW));
        String choice = sca.nextLine();
        String modelName = choice.equals("2") ? "gemma3:1b" : "qwen3:1.7b";
        OllamaClient ai = new OllamaClient(modelName);

        // 讀取進度並自動列印歷史紀錄
        List<Map<String, String>> history = MemoryStore.load();
        if (history != null) {
            System.out.println(UI.color("[系統] 偵測到存檔紀錄，已恢復調查進度。", UI.PURPLE));
            System.out.println(UI.color("------ 歷史調查紀錄回溯 ------", UI.YELLOW));
            for (Map<String, String> msg : history) {
                String role = msg.get("role");
                if (role.equals("assistant")) {
                    System.out.println(UI.color("AI 主持人 : ", UI.CYAN) + msg.get("content"));
                } else if (role.equals("user")) {
                    System.out.println(UI.color("玩家 > ", UI.GREEN) + msg.get("content"));
                }
            }
            System.out.println(UI.color("----------------------------", UI.YELLOW));
        }

        while (true) {
            // 新遊戲初始化
            if (history == null) {
                history = new ArrayList<>();
                Map<String, String> sysMsg = new HashMap<>();
                sysMsg.put("role", "system");
                sysMsg.put("content", systemPrompt);
                history.add(sysMsg);

                System.out.print(UI.color("AI 主持人 : \n", UI.CYAN));
                StringBuilder firstReply = new StringBuilder();
                ai.chatStream(history, (chunk) -> { 
                    System.out.print(chunk); 
                    firstReply.append(chunk); 
                });
                System.out.println();
                
                // 開場後，列印海龜湯類型資料庫讓玩家了解黑湯等分類
                UI.printTypeDatabase();

                Map<String, String> resMessage = new HashMap<>();
                resMessage.put("role", "assistant");
                resMessage.put("content", firstReply.toString());
                history.add(resMessage);
                MemoryStore.save(history);
            }

            System.out.print(UI.color("\n玩家 > ", UI.GREEN));
            String input = sca.nextLine();
            if (input.equalsIgnoreCase("exit")) break;

            // 在 while 迴圈內獲取玩家輸入後
            String processedInput = input;
            if (input.contains("提示") || input.contains("hint") || input.contains("暗示") || input.contains("我需要關鍵提示")) {
                processedInput = "（系統指令：玩家現在卡住了，請給予一個不破梗的模糊提示）" + input;
            }

            Map<String, String> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", processedInput); // 發送處理過的內容給 AI

            // 在處理 AI 回覆的區塊
            System.out.print(UI.color("AI 主持人 : ", UI.CYAN));
            StringBuilder reply = new StringBuilder();
            ai.chatStream(history, (chunk) -> { 
                System.out.print(chunk); 
                reply.append(chunk); 
            });
            System.out.println();

            String finalReply = reply.toString();
            
            Map<String, String> resMessage = new HashMap<>();
            resMessage.put("role", "assistant");
            resMessage.put("content", reply.toString());
            history.add(resMessage);
            MemoryStore.save(history);

            // 結案與重新開始判定
            if (history.size() > 2 && (finalReply.contains("[SUCCESS]") || finalReply.contains("[GAME_OVER]"))) {
                System.out.println(UI.color("\n[案件結案] 偵測到調查結束。", UI.PURPLE));
                System.out.print(UI.color("是否重新開始新遊戲？(y/n): ", UI.YELLOW));
                String restart = sca.nextLine().trim();
                if (restart.equalsIgnoreCase("y")) {
                    history = null; 
                    new File("current_save.txt").delete();
                    System.out.println(UI.color("[系統] 正在重新啟動終端並重新出題...", UI.PURPLE));
                } else {
                    break;
                }
            }
        }
        sca.close();
    }
}