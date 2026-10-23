# 取自 PIME 的檔案

這個目錄的檔案取自 [EasyIME/PIME](https://github.com/EasyIME/PIME)（commit e0ddf2f，2026-10-23），內容沒有修改。
它們**不適用** Andaime 的 Apache-2.0 授權，各自依下表的授權散佈。

| 檔案 | PIME 路徑 | 授權 |
|------|-----------|------|
| `dayi3.cin` | `python/cinbase/cin/dayi3.cin` | GPL v2（PIME 在同目錄附 `gpl.txt`，見 `LICENSES/GPL-2.0.txt`） |
| `dayi4.cin` | `python/cinbase/cin/dayi4.cin` | 太易資訊授權：開放給使用者下載使用；**不得更改字的編碼規則後重新散佈**，可改格式（見檔頭） |

用途：

- `dayi3.cin`、`dayi4.cin`：給 engine 單元測試讀取，也可以直接下載後匯入 Andaime。App 本身**不附**碼表。

App 內的大易符號表（`app/src/main/assets/dayi-symbols.txt`）是 Andaime 自己整理的，不是取自 PIME。

泰瑞四碼（`thdayi.cin`）沒有載明授權，所以不放在這裡，請到 PIME 下載。
