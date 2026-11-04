package main

import (
	"bufio"
	"bytes"
	"context"
	"crypto/sha256"
	"embed"
	"encoding/csv"
	"encoding/hex"
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
	"time"
)

//go:embed payload/*
var assets embed.FS

const versionID = "fabric-loader-0.20.1-1.21.11"
const profileID = "levrn-1.21.11"

func main() {
	code := run()
	if len(os.Args) == 1 && runtime.GOOS == "windows" {
		fmt.Print("\nPress Enter to close.")
		_, _ = bufio.NewReader(os.Stdin).ReadString('\n')
	}
	os.Exit(code)
}

func run() int {
	fmt.Println("LEVRN / Minecraft 1.21.11")
	attach := flag.String("attach", "", "attach to an existing Fabric Minecraft PID")
	list := flag.Bool("list", false, "list Java processes for attachment")
	mcDir := flag.String("minecraft-dir", defaultMinecraftDir(), "official Minecraft launcher directory")
	javaPath := flag.String("java", "", "Java 21 executable (JDK required for attachment)")
	flag.Parse()
	java, err := findJava(*javaPath, *mcDir, *attach != "" || *list)
	if err != nil {
		return fail(err)
	}
	cache, err := unpack()
	if err != nil {
		return fail(err)
	}
	if *list {
		return runJava(java, "--add-modules", "jdk.attach", "-jar", filepath.Join(cache, "levrn-agent.jar"), "--list")
	}
	if *attach != "" {
		return runJava(java, "--add-modules", "jdk.attach", "-jar", filepath.Join(cache, "levrn-agent.jar"), "--attach", *attach, filepath.Join(cache, "levrn-client.jar"))
	}
	if err := install(java, cache, *mcDir); err != nil {
		return fail(err)
	}
	fmt.Println("\nInstalled. Open the Minecraft Launcher, select 'Levrn 1.21.11', and press Play.")
	fmt.Println("Right Shift: menu / C: zoom / End: disable modules.")
	return 0
}

func fail(err error) int { fmt.Fprintln(os.Stderr, "Levrn:", err); return 1 }
func runJava(java string, args ...string) int {
	cmd := exec.Command(java, args...)
	cmd.Stdin, cmd.Stdout, cmd.Stderr = os.Stdin, os.Stdout, os.Stderr
	if err := cmd.Run(); err != nil {
		if exit, ok := err.(*exec.ExitError); ok {
			return exit.ExitCode()
		}
		return fail(err)
	}
	return 0
}

func defaultMinecraftDir() string {
	if runtime.GOOS == "windows" {
		return filepath.Join(os.Getenv("APPDATA"), ".minecraft")
	}
	home, _ := os.UserHomeDir()
	if runtime.GOOS == "darwin" {
		return filepath.Join(home, "Library", "Application Support", "minecraft")
	}
	return filepath.Join(home, ".minecraft")
}

func findJava(explicit, minecraft string, attach bool) (string, error) {
	candidates := []string{}
	if explicit != "" {
		candidates = append(candidates, explicit)
	} else {
		name := "java"
		if runtime.GOOS == "windows" {
			name += ".exe"
		}
		if home := os.Getenv("JAVA_HOME"); home != "" {
			candidates = append(candidates, filepath.Join(home, "bin", name))
		}
		if found, err := exec.LookPath(name); err == nil {
			candidates = append(candidates, found)
		}
		roots := []string{filepath.Join(minecraft, "runtime")}
		if runtime.GOOS == "windows" {
			roots = append(roots, filepath.Join(os.Getenv("ProgramFiles"), "Eclipse Adoptium"), filepath.Join(os.Getenv("ProgramFiles"), "Java"), filepath.Join(os.Getenv("LOCALAPPDATA"), "Programs", "Eclipse Adoptium"))
		}
		for _, root := range roots {
			_ = filepath.WalkDir(root, func(path string, entry os.DirEntry, err error) error {
				if err != nil {
					return nil
				}
				if !entry.IsDir() && strings.EqualFold(entry.Name(), name) && filepath.Base(filepath.Dir(path)) == "bin" {
					candidates = append(candidates, path)
				}
				return nil
			})
		}
	}
	for _, candidate := range candidates {
		ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		out, err := exec.CommandContext(ctx, candidate, "--list-modules").Output()
		cancel()
		if err == nil && bytes.Contains(out, []byte("java.base@21")) && (!attach || bytes.Contains(out, []byte("jdk.attach@21"))) {
			return candidate, nil
		}
	}
	return "", errors.New("Java 21 was not found. Install a Java 21 JDK, or pass --java <path-to-java.exe>. Attachment requires its jdk.attach module")
}

// Keep payloads in a content-addressed user cache: attached JVMs may load classes lazily.
func unpack() (string, error) {
	manifest, err := assets.ReadFile("payload/checksums.json")
	if err != nil {
		return "", err
	}
	sum := sha256.Sum256(manifest)
	base, err := os.UserCacheDir()
	if err != nil {
		return "", err
	}
	dest := filepath.Join(base, "Levrn", hex.EncodeToString(sum[:12]))
	if err := os.MkdirAll(dest, 0700); err != nil {
		return "", err
	}
	var checksums map[string]string
	if err := json.Unmarshal(manifest, &checksums); err != nil {
		return "", err
	}
	for name, expected := range checksums {
		if filepath.Base(name) != name {
			return "", errors.New("invalid embedded filename")
		}
		data, err := assets.ReadFile("payload/" + name)
		if err != nil {
			return "", err
		}
		hash := sha256.Sum256(data)
		if hex.EncodeToString(hash[:]) != expected {
			return "", fmt.Errorf("embedded checksum mismatch: %s", name)
		}
		if err := atomicWrite(filepath.Join(dest, name), data, 0600); err != nil {
			return "", err
		}
	}
	return dest, nil
}

func install(java, cache, mcDir string) error {
	// No process-killing or automatic changes to a live launcher's profile database.
	fmt.Println("Close Minecraft and the Minecraft Launcher before installation.")
	if runtime.GOOS == "windows" {
		ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		output, err := exec.CommandContext(ctx, "tasklist", "/FO", "CSV", "/NH").Output()
		if err != nil {
			return errors.New("could not check the launcher process; close Minecraft Launcher and retry")
		}
		if launcherOpen(output) {
			return errors.New("Minecraft Launcher is running; close it before installing to preserve its profile database")
		}
	}
	profiles := []string{}
	for _, name := range []string{"launcher_profiles.json", "launcher_profiles_microsoft_store.json"} {
		path := filepath.Join(mcDir, name)
		if _, err := os.Stat(path); err == nil {
			profiles = append(profiles, path)
		}
	}
	if len(profiles) == 0 {
		return errors.New("no official launcher profiles found; open the official Minecraft Launcher once, close it, then retry (or use --minecraft-dir)")
	}
	// Validate each file and destination before invoking the official installer.
	for _, path := range profiles {
		if _, err := mergeProfile(path, mcDir); err != nil {
			return err
		}
	}
	installer := filepath.Join(cache, "fabric-installer.jar")
	if code := runJava(java, "-jar", installer, "client", "-dir", mcDir, "-mcversion", "1.21.11", "-loader", "0.20.1", "-noprofile"); code != 0 {
		return fmt.Errorf("official Fabric installation failed (exit %d); profile and mod installation did not proceed", code)
	}
	return finishInstall(cache, mcDir, profiles)
}

func launcherOpen(output []byte) bool {
	reader := csv.NewReader(bytes.NewReader(output))
	reader.FieldsPerRecord = -1
	for {
		row, err := reader.Read()
		if err == io.EOF {
			return false
		}
		if err != nil {
			continue
		}
		if len(row) == 0 {
			continue
		}
		name := strings.ToLower(row[0])
		if name == "minecraftlauncher.exe" || name == "minecraft.exe" {
			return true
		}
	}
}

func finishInstall(cache, mcDir string, profiles []string) error {
	versionPath := filepath.Join(mcDir, "versions", versionID, versionID+".json")
	b, err := os.ReadFile(versionPath)
	if err != nil {
		return fmt.Errorf("Fabric version metadata missing: %w", err)
	}
	var meta map[string]json.RawMessage
	if err := json.Unmarshal(b, &meta); err != nil {
		return err
	}
	var id, inherited string
	_ = json.Unmarshal(meta["id"], &id)
	_ = json.Unmarshal(meta["inheritsFrom"], &inherited)
	if id != versionID || inherited != "1.21.11" {
		return errors.New("Fabric installer produced an unexpected version profile")
	}
	mods := filepath.Join(mcDir, "levrn", "1.21.11", "mods")
	if err := os.MkdirAll(mods, 0700); err != nil {
		return err
	}
	for _, item := range []struct{ source, target string }{{"levrn-client.jar", "levrn-0.1.0-alpha.jar"}, {"fabric-api.jar", "fabric-api-0.141.6+1.21.11.jar"}} {
		data, err := os.ReadFile(filepath.Join(cache, item.source))
		if err != nil {
			return err
		}
		dest := filepath.Join(mods, item.target)
		if old, err := os.ReadFile(dest); err == nil {
			if !bytes.Equal(old, data) {
				if err := backup(dest, old); err != nil {
					return err
				}
			}
		} else if !os.IsNotExist(err) {
			return err
		}
		if err := atomicWrite(dest, data, 0600); err != nil {
			return err
		}
	}
	for _, path := range profiles {
		data, err := mergeProfile(path, mcDir)
		if err != nil {
			return err
		}
		before, err := os.ReadFile(path)
		if err != nil {
			return err
		}
		if err := backup(path, before); err != nil {
			return err
		}
		if err := atomicWrite(path, data, 0600); err != nil {
			return err
		}
	}
	return nil
}

func mergeProfile(path, mcDir string) ([]byte, error) {
	b, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}
	var document map[string]json.RawMessage
	if err := json.Unmarshal(b, &document); err != nil || document == nil {
		return nil, fmt.Errorf("invalid launcher profile file: %s (left unchanged)", path)
	}
	profiles := map[string]json.RawMessage{}
	if p, found := document["profiles"]; found {
		if err := json.Unmarshal(p, &profiles); err != nil || profiles == nil {
			return nil, errors.New("invalid launcher profiles object; left unchanged")
		}
	}
	if old, found := profiles[profileID]; found {
		var existing struct {
			Managed bool `json:"levrnManaged"`
		}
		if err := json.Unmarshal(old, &existing); err != nil || !existing.Managed {
			return nil, errors.New("a profile named levrn-1.21.11 already exists and is not managed by Levrn; left unchanged")
		}
	}
	profile := map[string]any{
		"name": "Levrn 1.21.11", "type": "custom", "lastVersionId": versionID,
		"gameDir": filepath.Join(mcDir, "levrn", "1.21.11"), "levrnManaged": true,
	}
	profiles[profileID], err = json.Marshal(profile)
	if err != nil {
		return nil, err
	}
	document["profiles"], err = json.Marshal(profiles)
	if err != nil {
		return nil, err
	}
	return json.MarshalIndent(document, "", "  ")
}

func backup(path string, data []byte) error {
	hash := sha256.Sum256(data)
	name := path + ".levrn-backup-" + hex.EncodeToString(hash[:8])
	file, err := os.OpenFile(name, os.O_CREATE|os.O_EXCL|os.O_WRONLY, 0600)
	if os.IsExist(err) {
		existing, readErr := os.ReadFile(name)
		if readErr == nil && bytes.Equal(existing, data) {
			return nil
		}
		return errors.New("existing backup does not match original; installation stopped")
	}
	if err != nil {
		return err
	}
	_, writeErr := file.Write(data)
	closeErr := file.Close()
	if writeErr != nil {
		return writeErr
	}
	return closeErr
}

func atomicWrite(path string, data []byte, mode os.FileMode) error {
	if old, err := os.ReadFile(path); err == nil && bytes.Equal(old, data) {
		return nil
	}
	file, err := os.CreateTemp(filepath.Dir(path), ".levrn-*")
	if err != nil {
		return err
	}
	temp := file.Name()
	defer os.Remove(temp)
	if err := file.Chmod(mode); err != nil {
		file.Close()
		return err
	}
	if _, err := io.Copy(file, bytes.NewReader(data)); err != nil {
		file.Close()
		return err
	}
	if err := file.Sync(); err != nil {
		file.Close()
		return err
	}
	if err := file.Close(); err != nil {
		return err
	}
	return os.Rename(temp, path)
}
