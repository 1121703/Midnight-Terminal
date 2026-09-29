# 午夜終端 (Midnight Terminal) — 原創海龜湯推理系統

一款基於本地端大型語言模型（Local LLM）的互動式海龜湯情境推理終端遊戲。本專案以 Java 實作，透過 HTTP Client 串接 Ollama API，具備打字機串流輸出、階梯式提示機制、雙模型切換與自動進度存檔功能。

---

## 📌 相關連結

* **專案簡報**：[Canva Presentation](https://canva.link/uxl888z3g0k2iy2)
* **GitHub 專案庫**：本專案儲存庫

---

## 👥 開發成員 (Developers)

**元智大學 資訊工程學系**（Java 程式設計專題）
* **1121703** 鐘怡婷
* **1121553** 翁榆涵

---

## 🌟 核心特色與亮點 (Key Features)

* **AI 原創情境出題**：透過嚴密設計的 System Prompt，要求模型避開經典網路考古題，動態生成涵蓋「清湯 / 紅湯 / 黑湯」、「本格 / 變格」、「隱情 / 場景型」之多元原創謎題。
* **低延遲串流輸出 (Streaming)**：採用 Java HTTP Client 處理 Ollama Streaming API，實現打字機般的流暢逐字回傳體驗。
* **嚴謹是非狀態機**：嚴格限制 AI 僅能回答「是 / 不是 / 無關」，避免模型提前劇透；並內建 `[SUCCESS]` 與 `[GAME_OVER]` 狀態標籤判定結案。
* **階梯式提示機制**：輸入「提示」或「我需要關鍵提示」時，系統會自動包裝前綴指令，依序提供環境氛圍或關鍵物體線索而不破梗。
* **雙核心模型切換**：啟動時可依需求自由選擇推論核心：
  * `1`: `qwen3:1.7b`（中文語意強）
  * `2`: `gemma3:1b`（英文邏輯強）
* **ANSI 終端彩色渲染**：封裝自定義 `UI` 類別，針對 AI 主持人、玩家輸入、提示、警告及故事類型資料庫套用 ANSI Color Codes 視覺化顯示。
* **持久化進度保存 (MemoryStore)**：對話紀錄自動同步至本地檔案 `current_save.txt`，支援中斷重啟後的進度無縫回溯與調查復原。

---

## 🛠 技術架構 (Tech Stack)

* **語言與環境**：Java 11+
* **推論核心**：[Ollama](https://ollama.com/) 本地模型服務 (`qwen3:1.7b` / `gemma3:1b`)
* **通訊協定**：Java Native `java.net.http.HttpClient` (REST API / JSON Streaming)
* **架構設計**：模組化 OOP（主邏輯 `Mid_Project`、用戶端 `OllamaClient`、儲存層 `MemoryStore`、介面層 `UI`）

---

## 🚀 執行說明 (Getting Started)

### 1. 前置需求

確保本機已安裝並啟動 Ollama 服務，且已下載所需模型：

```bash
# 啟動 Ollama 服務（若尚未背景啟動）
ollama serve

# 下載支援之模型（二選一或皆下載）
ollama pull qwen3:1.7b
ollama pull gemma3:1b

```

### 2. 編譯與執行

在專案目錄下透過終端機執行：

```bash
# 編譯程式碼
javac -encoding UTF-8 Mid_Project.java

# 執行主程式
java Mid_Project

```

### 3. 操作指令指南

* 提問：直接輸入想確認的情境是非題（例：`死者是人類嗎？`）。
* 取得提示：輸入 `提示`、`hint` 或 `我需要關鍵提示`。
* 揭曉真相：輸入 `揭曉真相` 或 `我想看湯底` 直接結束並觀看完整解答。
* 離開系統：輸入 `exit`。