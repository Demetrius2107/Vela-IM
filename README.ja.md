<div align="center">

# Vela — エンタープライズ IM & オフィスエコシステムプラットフォーム

**Spring Boot + Netty + Vue 3 + Kotlin Multiplatform フルスタック IM ソリューション**

[**中文**](README.md) | [**English**](README.en.md) | [**日本語**](README.ja.md)

![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)
![Java](https://img.shields.io/badge/Java-17%20%2F%208-orange.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.3.2-brightgreen.svg)
![Netty](https://img.shields.io/badge/Netty-4.1-green.svg)
![Vue 3](https://img.shields.io/badge/Vue-3-4FC08D.svg)
![Build Status](https://img.shields.io/badge/build-passing-brightgreen.svg)

</div>

---

## 目次

- [概要](#概要)
- [機能](#機能)
- [技術スタック](#技術スタック)
- [アーキテクチャ](#アーキテクチャ)
- [クイックスタート](#クイックスタート)
- [プロジェクト構成](#プロジェクト構成)
- [クライアント](#クライアント)
- [ドキュメント](#ドキュメント)
- [開発ガイドライン](#開発ガイドライン)
- [コントリビューション](#コントリビューション)
- [ライセンス](#ライセンス)

---

## 概要

Vela は **IM インスタントメッセージング、管理コンソール、オフィスエコシステム、音声/ビデオ通話** をカバーするフルスタックプロジェクトです。バックエンドは **DDD ヘキサゴナルアーキテクチャ** に基づき、**TCP / WebSocket** のデュアルプロトコル長接続をサポート。フロントエンドは **Web / Android / Electron / Flutter / iOS** のマルチプラットフォームをカバーします。

- フルリンク接続: TCP ゲートウェイ → ビジネスサービス → メッセージストア、Phase 1 接続テスト 11/11 合格
- 信頼性の高い配信: ACK 再送、指数バックオフ再試行、オフラインメッセージの増分取得、DB デグレード補償
- マイクロサービスアーキテクチャ: 12 モジュールを DDD レイヤで分割、MQ 非同期分離、個別デプロイ可能

### コアアーキテクチャ

```
Client ──TCP/WS──→ vela-tcp(ゲートウェイ) ──MQ──→ vela-service(ビジネス) ──MQ──→ vela-message-store(ストレージ)
                       │                              │
                       ├── Redis (キャッシュ/セッション)  ├── MySQL (永続化)
                       ├── RabbitMQ (イベント)          ├── Elasticsearch (全文検索)
                       └── ZooKeeper (レジストリ)        └── Logstash (ログ収集)
                                                             └── Kibana (可視化)
```

### 統計

| 指標 | 値 |
|:----|:----:|
| Java ソース | ~400+ ファイル |
| ユニットテスト | 123 件 |
| REST エンドポイント | 60+ 件 |
| サービスモジュール | 12 個 |
| Docker コンテナ | 16 個 |
| Git コミット | 100+ 件 |

---

## 機能

### IM コア（Phase 0-4）✅

| モジュール | 機能 |
|:----|:------|
| テキストメッセージ | P2P + グループチャット送受信、ACK、重複排除、マルチデバイス同期 |
| メッセージ取り消し | 設定可能な取り消しウィンドウ + 時計ズレ耐性 |
| 既読通知 | 個別・グループチャットの既読通知 |
| オフラインメッセージ | Redis ZSet 増分取得 + 上限超過時の DB デグレード |
| 会話管理 | ピン留め / おやすみモード / 削除 / 既読マーク |
| 友達関係 | CRUD / グループ分け / ブラックリスト / リクエスト承認 |
| グループ管理 | 作成/解散/ミュート/権限移譲/ロール/お知らせ/投票 |
| マルチデバイスログイン | 4 ポリシー（単一デバイス〜無制限）|
| TCP/WS ゲートウェイ | Netty デュアルプロトコル + ハートビート + レジストリ検出 |
| トレーサビリティ | MDC TraceId 全チェーン伝播 |

### L2 例外境界（Phase 0.5）✅

| 機能 | 説明 |
|:----|:------|
| メッセージ再試行 | 指数バックオフ（設定可能、3 回）|
| ACK 再送 | PendingAckTracker + 定期スキャン |
| デグレードフレームワーク | ServiceDegradationManager（Redis/MQ サーキットブレーカー）|
| DB 補償 | MessageCompensationStore + 定期再試行 |
| 並行ロック | MessageLockManager（ReadWriteLock で取り消し ↔ プッシュを調整）|
| 時計耐性 | 設定可能な時計ズレ + 逆ズレチェック |

### 管理コンソール（Phase 5）✅

| モジュール | 機能 |
|:----|:------|
| ダッシュボード | 統計カード + メッセージトレンド + トップ 10 グループ |
| ユーザー管理 | 検索/ページング/詳細/一括無効化/ログインログ |
| グループ管理 | 一覧/ステータスフィルタ/詳細/解散/エクスポート |
| メッセージ監査 | ES 全文検索 + SQL LIKE フォールバック |
| 操作ログ | すべての管理操作を自動記録 |
| 管理者 | スーパー/オペレーター/監査役の 3 段階ロール |
| システム設定 | 動的パラメータ調整 |

### オフィスエコシステム（Phase 6）✅

| モジュール | 機能 |
|:----|:------|
| スケジュール | 作成/一覧/ステータス/削除 |
| TODO | 作成/一覧/優先度/完了 |
| 承認フロー | 提出/承認/却下 |
| ナレッジベース | ドキュメント CRUD + オンラインエディタ |
| Bot マーケット | Bot インストール/購読/管理 + コマンド設定 |
| メッセージお気に入り | お気に入り CRUD + クロスデバイス同期 |

---

## 技術スタック

| カテゴリ | 技術 | 用途 |
|------|------|------|
| 言語 | Java 8/17 + Kotlin | バックエンド + Android |
| フレームワーク | Spring Boot 2.3.2 | サービスコンテナ |
| ネットワーク | Netty 4.1 | TCP/WebSocket 長接続 |
| ORM | MyBatis-Plus 3.4.2 | データベースアクセス |
| キャッシュ | Redis 6.2 | セッション/オフラインメッセージ/シーケンス |
| メッセージキュー | RabbitMQ 3.8 | 非同期分離/イベント駆動 |
| レジストリ | ZooKeeper 3.6 | ゲートウェイノード検出 |
| 全文検索 | Elasticsearch 7.17 | メッセージ検索 + ログ保存 |
| ログ収集 | Logstash + Kibana 7.17 | ELK ログ基盤 |
| シリアライズ | Protostuff | TCP プロトコルコーデック |
| フロントエンド | Vue 3 + Naive UI | Web IM クライアント |
| デスクトップ | Electron 28 | デスクトップ IM クライアント |
| モバイル | Kotlin + Jetpack Compose | Android クライアント |
| モニタリング | Prometheus + Grafana + SkyWalking | メトリクス/APM |
| ビルド | Maven + Gradle | バックエンド + Android |

---

## アーキテクチャ

DDD レイヤリングに従う: **interfaces → application → domain ← infrastructure**。モジュール間の参照は単方向依存のみ許可。

詳細設計ドキュメントは [`docs/architecture/`](docs/architecture/) にあります:

| ドキュメント | 説明 |
|------|------|
| [system-architecture.md](docs/architecture/system-architecture.md) | システム全体アーキテクチャ |
| [DDD-Hexagonal-Architecture.md](docs/architecture/DDD-Hexagonal-Architecture.md) | DDD ヘキサゴナルアーキテクチャ設計 |
| [concurrent-conflict-handling.md](docs/architecture/concurrent-conflict-handling.md) | 並行競合処理 |
| [e2e-encryption-design.md](docs/architecture/e2e-encryption-design.md) | エンドツーエンド暗号化（E2EE）設計 |

---

## クイックスタート

### Docker ワンクリック起動（推奨）

```bash
# 1. バックエンドをビルド
mvn clean package -DskipTests -q

# 2. 全サービスを起動
docker-compose up -d
```

### 手動起動

```bash
# 1. ミドルウェアを起動: MySQL / Redis / RabbitMQ / ZooKeeper
docker-compose -f docker-compose.middleware.yml up -d

# 2. API ゲートウェイを起動（ポート 8889）
cd vela-gateway && mvn spring-boot:run

# 3. ビジネスサービスを起動（user / friendship / group / message / conversation ...）
cd vela-service-user && mvn spring-boot:run

# 4. TCP/WS ゲートウェイを起動（ポート 9000）
cd vela-tcp && mvn spring-boot:run

# 5. フロントエンドを起動
cd web && npm install && npm run dev
```

> デプロイとトラブルシューティングは [`docs/guide/deployment-guide.md`](docs/guide/deployment-guide.md) と [`docs/guide/docker-troubleshooting.md`](docs/guide/docker-troubleshooting.md) を参照してください。

### アクセスエンドポイント

| エントリ | URL |
|:----|:-----|
| IM Web | http://localhost:3000 |
| 管理コンソール | http://localhost:3000/#/admin |
| オフィスエコシステム | http://localhost:3000/#/office |
| Kibana | http://localhost:5601 |
| Grafana | http://localhost:3000 (admin/admin) |

---

## プロジェクト構成

```
Vela/
├── vela-common/           # 共有カーネル（enum/定数/メッセージタイプ/設定）
├── vela-codec/            # インフラ: TCP/WS プロトコルコーデック
├── vela-tcp/              # アダプターレイヤ: Netty TCP/WS ゲートウェイ
├── vela-gateway/          # API ゲートウェイ
├── vela-service-*/        # ビジネスサービス（DDD レイヤ、12 モジュール）
│   ├── user/              # ユーザードメイン
│   ├── friendship/        # 友達関係ドメイン
│   ├── group/             # グループドメイン（お知らせ/投票/タグ/ファイル）
│   ├── message/           # メッセージドメイン（ES 検索/既読追跡）
│   ├── conversation/      # 会話ドメイン
│   ├── admin/             # 管理コンソール
│   ├── bot/               # Bot
│   ├── office/            # オフィスエコシステム（スケジュール/TODO/承認）
│   └── ...
├── vela-message-store/    # インフラ: メッセージ永続化サービス
├── web/                   # Vue 3 フロントエンド（IM/管理/オフィス）
├── android/               # Android クライアント（Kotlin + Compose）
├── electron/              # Electron デスクトップクライアント
├── flutter_desktop/       # Flutter デスクトップ（実験的）
├── ios/                   # iOS クライアント（SwiftUI）
├── deploy/                # デプロイ設定（Logstash/Prometheus/スクリプト）
├── docs/                  # ドキュメントセンター
│   ├── guide/             # デプロイ/Docker/結合テストガイド
│   ├── analysis/          # ギャップ分析/機能比較
│   ├── roadmap/           # ロードマップ/TODO リスト
│   ├── architecture/      # アーキテクチャ設計ドキュメント
│   ├── api/               # REST API ドキュメント
│   ├── logs/              # 過去のランタイムログアーカイブ
│   └── 会议记录/           # セッション作業記録
├── docker-compose.yml     # 16 コンテナオーケストレーション
└── AGENTS.md              # プロジェクト開発ガイドライン（AI 支援コーディング）
```

---

## クライアント

| プラットフォーム | ステータス | 説明 |
|:----|:----:|:------|
| Web (Vue 3) | ✅ | フル IM + 管理コンソール + オフィスエコシステム |
| Android (Compose) | ✅ | ログイン/登録/会話一覧/チャット/連絡先 |
| Electron デスクトップ | ✅ | Web ラッパー + システムトレイ + ウィンドウ管理 |
| Flutter デスクトップ | 🚧 | 実験的なクロスプラットフォーム |
| iOS (SwiftUI) | 🚧 | ネイティブクライアント開発中 |

---

## ドキュメント

| カテゴリ | ドキュメント |
|------|------|
| API リファレンス | [`docs/api/api-documentation.md`](docs/api/api-documentation.md) |
| デプロイガイド | [`docs/guide/deployment-guide.md`](docs/guide/deployment-guide.md) |
| Docker ガイド | [`docs/guide/docker-complete-guide.md`](docs/guide/docker-complete-guide.md) |
| Docker トラブルシューティング | [`docs/guide/docker-troubleshooting.md`](docs/guide/docker-troubleshooting.md) |
| 結合テスト計画 | [`docs/guide/integration-testing-plan.md`](docs/guide/integration-testing-plan.md) |
| ギャップ分析 | [`docs/analysis/current-state-gap-analysis.md`](docs/analysis/current-state-gap-analysis.md) |
| 機能比較 | [`docs/analysis/feature-gap-analysis.md`](docs/analysis/feature-gap-analysis.md) |
| ロードマップ | [`docs/roadmap/feature-roadmap.md`](docs/roadmap/feature-roadmap.md) |
| 起動問題チェックリスト | [`docs/Vela项目启动问题完整清单.md`](docs/Vela项目启动问题完整清单.md) |
| MySQL リファクタ | [`docs/MySQL/database-refactor-plan.md`](docs/MySQL/database-refactor-plan.md) |

---

## 開発ガイドライン

[`AGENTS.md`](AGENTS.md) を参照してください。コアルール:

```
1. DDD レイヤリング: interfaces → application → domain ← infrastructure
2. コンストラクタインジェクション（@Autowired ではなく）
3. 関数は 50 行未満、ハードコード定数は設定に抽出
4. 新規エンティティ → 対応 DDL、メッセージモデル変更 → OfflineMessageContent 更新
5. コメントは「何を」ではなく「なぜ」を書く
6. Git コミット形式: <type>(<scope>): <subject>
```

---

## コントリビューション

1. リポジトリをフォークし、機能ブランチを作成: `git checkout -b feat/<description>`
2. [`AGENTS.md`](AGENTS.md) のコーディング・コミットガイドラインに従う
3. 提出前に `mvn -B clean compile` を実行
4. `master` ブランチへ Pull Request を送信

---

## ライセンス

このプロジェクトは [MIT License](LICENSE) のもとでオープンソース化されています。

---

> Copyright © 2026 Vela Contributors. Released under the MIT License.
