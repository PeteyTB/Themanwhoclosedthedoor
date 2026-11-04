package main

import (
	"archive/zip"
	"bytes"
	"encoding/json"
	"os"
	"path/filepath"
	"testing"
)

func fixture(t *testing.T) (string, string, string) {
	t.Helper()
	root := t.TempDir()
	mc, cache := filepath.Join(root, "Minecraft with spaces"), filepath.Join(root, "cache")
	if err := os.MkdirAll(mc, 0700); err != nil {
		t.Fatal(err)
	}
	if err := os.MkdirAll(cache, 0700); err != nil {
		t.Fatal(err)
	}
	profiles := filepath.Join(mc, "launcher_profiles.json")
	if err := os.WriteFile(profiles, []byte(`{"profiles":{"vanilla":{"name":"Keep me","lastVersionId":"1.21.11"}},"settings":{"keep":true},"authenticationDatabase":{"sentinel":"preserve"}}`), 0600); err != nil {
		t.Fatal(err)
	}
	return mc, cache, profiles
}

func TestProfilePreservesExistingDataAndIsRepeatable(t *testing.T) {
	mc, _, path := fixture(t)
	result, err := mergeProfile(path, mc)
	if err != nil {
		t.Fatal(err)
	}
	var document map[string]json.RawMessage
	if err := json.Unmarshal(result, &document); err != nil {
		t.Fatal(err)
	}
	if !bytes.Contains(document["authenticationDatabase"], []byte("preserve")) || !bytes.Contains(document["settings"], []byte("true")) {
		t.Fatal("unrelated launcher data lost")
	}
	var profiles map[string]json.RawMessage
	_ = json.Unmarshal(document["profiles"], &profiles)
	if _, ok := profiles["vanilla"]; !ok {
		t.Fatal("existing profile lost")
	}
	var own map[string]any
	_ = json.Unmarshal(profiles[profileID], &own)
	if own["gameDir"] != filepath.Join(mc, "levrn", "1.21.11") || own["lastVersionId"] != versionID {
		t.Fatal("wrong Levrn profile")
	}
	if err := os.WriteFile(path, result, 0600); err != nil {
		t.Fatal(err)
	}
	second, err := mergeProfile(path, mc)
	if err != nil || !bytes.Equal(result, second) {
		t.Fatal("profile install not repeatable", err)
	}
}

func TestMalformedAndConflictingProfilesArePreserved(t *testing.T) {
	for _, input := range []string{`not json`, `null`, `{"profiles":null}`, `{"profiles":{"levrn-1.21.11":{"name":"user-owned"}}}`} {
		t.Run(input, func(t *testing.T) {
			mc, _, path := fixture(t)
			_ = os.WriteFile(path, []byte(input), 0600)
			if _, err := mergeProfile(path, mc); err == nil {
				t.Fatal("bad input accepted")
			}
			actual, _ := os.ReadFile(path)
			if string(actual) != input {
				t.Fatal("input modified on failure")
			}
		})
	}
}

func TestInstallCopiesModsAndCreatesRecoverableBackups(t *testing.T) {
	mc, cache, profiles := fixture(t)
	original, _ := os.ReadFile(profiles)
	versionDir := filepath.Join(mc, "versions", versionID)
	_ = os.MkdirAll(versionDir, 0700)
	_ = os.WriteFile(filepath.Join(versionDir, versionID+".json"), []byte(`{"id":"`+versionID+`","inheritsFrom":"1.21.11"}`), 0600)
	_ = os.WriteFile(filepath.Join(cache, "levrn-client.jar"), []byte("client fixture"), 0600)
	_ = os.WriteFile(filepath.Join(cache, "fabric-api.jar"), []byte("api fixture"), 0600)
	if err := finishInstall(cache, mc, []string{profiles}); err != nil {
		t.Fatal(err)
	}
	b, err := os.ReadFile(filepath.Join(mc, "levrn", "1.21.11", "mods", "levrn.jar"))
	if err != nil || string(b) != "client fixture" {
		t.Fatal("client not installed", err)
	}
	backups, _ := filepath.Glob(profiles + ".levrn-backup-*")
	if len(backups) != 1 {
		t.Fatal("backup missing")
	}
	saved, _ := os.ReadFile(backups[0])
	if !bytes.Equal(saved, original) {
		t.Fatal("backup does not preserve original")
	}
	if err := finishInstall(cache, mc, []string{profiles}); err != nil {
		t.Fatal("repeat install failed", err)
	}
	var legacy bytes.Buffer
	writer := zip.NewWriter(&legacy)
	entry, err := writer.Create("fabric.mod.json")
	if err != nil {
		t.Fatal(err)
	}
	_, _ = entry.Write([]byte(`{"id":"levrn","version":"0.1.0-alpha"}`))
	if err := writer.Close(); err != nil {
		t.Fatal(err)
	}
	oldPath := filepath.Join(mc, "levrn", "1.21.11", "mods", "levrn-0.1.0-alpha.jar")
	if err := os.WriteFile(oldPath, legacy.Bytes(), 0600); err != nil {
		t.Fatal(err)
	}
	if err := finishInstall(cache, mc, []string{profiles}); err != nil {
		t.Fatal("legacy upgrade failed", err)
	}
	if _, err := os.Stat(oldPath); !os.IsNotExist(err) {
		t.Fatal("duplicate old client remains")
	}
	oldBackups, _ := filepath.Glob(oldPath + ".levrn-backup-*")
	if len(oldBackups) != 1 {
		t.Fatal("legacy client backup missing")
	}
}

func TestProfileUpgradePreservesCustomSettings(t *testing.T) {
	mc, _, path := fixture(t)
	data, err := mergeProfile(path, mc)
	if err != nil {
		t.Fatal(err)
	}
	var doc map[string]any
	if err := json.Unmarshal(data, &doc); err != nil {
		t.Fatal(err)
	}
	profile := doc["profiles"].(map[string]any)[profileID].(map[string]any)
	delete(profile, "levrnManaged")
	profile["javaArgs"] = "-Xmx4G"
	data, _ = json.Marshal(doc)
	if err := os.WriteFile(path, data, 0600); err != nil {
		t.Fatal(err)
	}
	updated, err := mergeProfile(path, mc)
	if err != nil {
		t.Fatal(err)
	}
	if !bytes.Contains(updated, []byte("-Xmx4G")) {
		t.Fatal("custom JVM settings lost during upgrade")
	}
}

func TestWrongFabricVersionDoesNotWriteProfiles(t *testing.T) {
	mc, cache, path := fixture(t)
	before, _ := os.ReadFile(path)
	dir := filepath.Join(mc, "versions", versionID)
	_ = os.MkdirAll(dir, 0700)
	_ = os.WriteFile(filepath.Join(dir, versionID+".json"), []byte(`{"id":"wrong","inheritsFrom":"1.21.11"}`), 0600)
	if err := finishInstall(cache, mc, []string{path}); err == nil {
		t.Fatal("invalid Fabric metadata accepted")
	}
	after, _ := os.ReadFile(path)
	if !bytes.Equal(before, after) {
		t.Fatal("profile changed")
	}
}

func TestUnpackEmbeddedPayloads(t *testing.T) {
	t.Setenv("XDG_CACHE_HOME", t.TempDir())
	path, err := unpack()
	if err != nil {
		t.Fatal(err)
	}
	for _, name := range []string{"fabric-installer.jar", "fabric-api.jar", "levrn-agent.jar", "levrn-client.jar"} {
		info, err := os.Stat(filepath.Join(path, name))
		if err != nil || info.Size() == 0 {
			t.Fatal("missing embedded payload", name, err)
		}
	}
	again, err := unpack()
	if err != nil || path != again {
		t.Fatal("unpack not repeatable", err)
	}
}

func TestLauncherProcessDetection(t *testing.T) {
	if !launcherOpen([]byte("\"MinecraftLauncher.exe\",\"123\",\"Console\"\n")) {
		t.Fatal("launcher was not detected")
	}
	if !launcherOpen([]byte("\"Minecraft.exe\",\"456\",\"Console\"\n")) {
		t.Fatal("store launcher was not detected")
	}
	if launcherOpen([]byte("\"javaw.exe\",\"789\",\"Console\"\n")) {
		t.Fatal("unrelated Java process blocked installation")
	}
}
