#!/usr/bin/env python3
"""
Zenith Package (.zpk) & Extension Repository Packaging Tool.

Commands:
  pack     - Assemble a .zpk package bundle from manifest and binaries.
  index    - Generate index.json repository catalog from a folder of .zpk packages.
  verify   - Verify .zpk integrity, manifest format, and Ed25519 signature.
  keygen   - Generate an Ed25519 keypair for signing extension packages.
"""

import argparse
import hashlib
import json
import os
import shutil
import sys
import zipfile
from pathlib import Path

try:
    from cryptography.hazmat.primitives.asymmetric import ed25519
    from cryptography.exceptions import InvalidSignature
    HAS_CRYPTO = True
except ImportError:
    HAS_CRYPTO = False


def cmd_pack(args):
    manifest_path = Path(args.manifest)
    if not manifest_path.exists():
        print(f"Error: Manifest not found at {manifest_path}", file=sys.stderr)
        return 1

    with open(manifest_path, "r", encoding="utf-8") as f:
        manifest_data = json.load(f)

    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)

    jar_path = Path(args.jar) if args.jar else None
    dex_path = Path(args.dex) if args.dex else None
    settings_path = Path(args.settings) if args.settings else None
    icon_path = Path(args.icon) if args.icon else None

    # Determine binary to sign
    primary_binary_bytes = None
    if getattr(args, "prefer_dex", False) and dex_path and dex_path.exists():
        primary_binary_bytes = dex_path.read_bytes()
    elif jar_path and jar_path.exists():
        primary_binary_bytes = jar_path.read_bytes()
    elif dex_path and dex_path.exists():
        primary_binary_bytes = dex_path.read_bytes()

    signature_bytes = None
    if args.private_key:
        if not HAS_CRYPTO:
            print("Error: cryptography package required for Ed25519 signing", file=sys.stderr)
            return 1
        if not primary_binary_bytes:
            print("Error: Cannot sign package without provider.jar or provider.dex", file=sys.stderr)
            return 1

        pk_raw = args.private_key.strip()
        if os.path.exists(pk_raw):
            pk_bytes = Path(pk_raw).read_bytes()
            if len(pk_bytes) == 64:  # Hex string file
                pk_bytes = bytes.fromhex(pk_bytes.decode("ascii").strip())
        else:
            pk_bytes = bytes.fromhex(pk_raw)

        if len(pk_bytes) != 32:
            print(f"Error: Expected 32-byte Ed25519 seed, got {len(pk_bytes)} bytes", file=sys.stderr)
            return 1

        priv_key = ed25519.Ed25519PrivateKey.from_private_bytes(pk_bytes)
        signature_bytes = priv_key.sign(primary_binary_bytes)
        pub_key = priv_key.public_key()
        pub_hex = pub_key.public_bytes_raw().hex()
        manifest_data["publicKey"] = pub_hex

    with zipfile.ZipFile(output_path, "w", zipfile.ZIP_DEFLATED) as zf:
        # 1. Manifest
        manifest_json_str = json.dumps(manifest_data, indent=2, ensure_ascii=False)
        zf.writestr("manifest.json", manifest_json_str.encode("utf-8"))

        # 2. JAR
        if jar_path and jar_path.exists():
            entry_name = manifest_data.get("entry", {}).get("jvm", "provider.jar")
            zf.write(jar_path, entry_name)

        # 3. DEX
        if dex_path and dex_path.exists():
            entry_name = manifest_data.get("entry", {}).get("android", "provider.dex")
            zf.write(dex_path, entry_name)

        # 4. Settings
        if settings_path and settings_path.exists():
            entry_name = manifest_data.get("settings", "settings.json")
            zf.write(settings_path, entry_name)

        # 5. Icon
        if icon_path and icon_path.exists():
            entry_name = manifest_data.get("icon", "icon.png")
            zf.write(icon_path, entry_name)

        # 6. Signature
        if signature_bytes:
            zf.writestr("signature.sig", signature_bytes)

    sha256 = hashlib.sha256(output_path.read_bytes()).hexdigest()
    print(f"Packed {manifest_data.get('id', 'plugin')} -> {output_path}")
    print(f"Size: {output_path.stat().st_size} bytes | SHA-256: {sha256}")
    return 0


def cmd_index(args):
    repo_dir = Path(args.repo_dir)
    if not repo_dir.exists():
        repo_dir.mkdir(parents=True, exist_ok=True)

    icons_dir = repo_dir / "icons"
    icons_dir.mkdir(parents=True, exist_ok=True)

    base_url = args.base_url.rstrip("/") if args.base_url else ""
    zpk_files = sorted(repo_dir.glob("*.zpk"))

    # Copy repo icon if present in project root or current dir
    repo_icon_candidates = [
        Path("icon.png"),
        repo_dir / "icon.png",
        repo_dir.parent.parent / "icon.png",
    ]
    for candidate in repo_icon_candidates:
        if candidate.exists() and candidate != (repo_dir / "icon.png"):
            shutil.copy2(candidate, repo_dir / "icon.png")
            break

    entries = []
    for zpk_path in zpk_files:
        try:
            with zipfile.ZipFile(zpk_path, "r") as zf:
                if "manifest.json" not in zf.namelist():
                    print(f"Warning: Skipping {zpk_path.name} (missing manifest.json)")
                    continue
                manifest_data = json.loads(zf.read("manifest.json").decode("utf-8"))

                icon_name = manifest_data.get("icon") or "icon.png"
                provider_id = manifest_data.get("id")
                entry_icon_url = None
                if icon_name in zf.namelist() and provider_id:
                    icon_target = icons_dir / f"{provider_id}.png"
                    icon_target.write_bytes(zf.read(icon_name))
                    entry_icon_url = f"{base_url}/icons/{provider_id}.png" if base_url else f"icons/{provider_id}.png"
        except Exception as e:
            print(f"Warning: Failed to read {zpk_path.name}: {e}")
            continue

        zpk_bytes = zpk_path.read_bytes()
        sha256 = hashlib.sha256(zpk_bytes).hexdigest()
        artifact_url = f"{base_url}/{zpk_path.name}" if base_url else zpk_path.name

        artifact = {
            "url": artifact_url,
            "sha256": sha256
        }

        entry = {
            "id": manifest_data.get("id"),
            "name": manifest_data.get("name", ""),
            "version": manifest_data.get("version", "1.0.0"),
            "capabilities": manifest_data.get("capabilities", []),
            "entryClass": manifest_data.get("entryClass", ""),
            "androidArtifact": artifact,
            "desktopArtifact": artifact,
            "icon": entry_icon_url or manifest_data.get("icon"),
            "homepage": manifest_data.get("homepage"),
            "description": manifest_data.get("description", "")
        }
        entries.append(entry)

    catalog = {
        "version": 1,
        "name": getattr(args, "repo_name", None) or "Zenith Community Repository",
        "description": getattr(args, "repo_desc", None) or "Официальный репозиторий плагинов сообщества Zenith",
        "icon": getattr(args, "repo_icon", None) or (f"{base_url}/icon.png" if base_url else "icon.png"),
        "providers": entries
    }

    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(catalog, f, indent=2, ensure_ascii=False)

    print(f"Generated index catalog with {len(entries)} provider(s) -> {output_path}")
    return 0


def cmd_verify(args):
    zpk_path = Path(args.package)
    if not zpk_path.exists():
        print(f"Error: Package not found: {zpk_path}", file=sys.stderr)
        return 1

    with zipfile.ZipFile(zpk_path, "r") as zf:
        names = zf.namelist()
        if "manifest.json" not in names:
            print("Error: Missing manifest.json", file=sys.stderr)
            return 1

        manifest = json.loads(zf.read("manifest.json").decode("utf-8"))
        print(f"Plugin: {manifest.get('name')} ({manifest.get('id')}) v{manifest.get('version')}")

        pub_key_hex = manifest.get("publicKey")
        sig_bytes = zf.read("signature.sig") if "signature.sig" in names else None

        if pub_key_hex:
            if not sig_bytes:
                print("Error: Public key declared but signature.sig missing", file=sys.stderr)
                return 1
            if not HAS_CRYPTO:
                print("Warning: cryptography package not available, skipping signature verification")
            else:
                pub_bytes = bytes.fromhex(pub_key_hex.replace("ed25519:", "").strip())
                pub_key = ed25519.Ed25519PublicKey.from_public_bytes(pub_bytes)

                # Match binary
                jvm_entry = manifest.get("entry", {}).get("jvm", "provider.jar")
                binary_bytes = zf.read(jvm_entry) if jvm_entry in names else None
                if not binary_bytes:
                    dex_entry = manifest.get("entry", {}).get("android", "provider.dex")
                    binary_bytes = zf.read(dex_entry) if dex_entry in names else None

                if binary_bytes:
                    try:
                        pub_key.verify(sig_bytes, binary_bytes)
                        print("Signature: VALID (Ed25519 verified)")
                    except InvalidSignature:
                        print("Signature: INVALID (Verification failed)", file=sys.stderr)
                        return 1

    print("Package integrity: OK")
    return 0


def cmd_keygen(args):
    if not HAS_CRYPTO:
        print("Error: cryptography package required for Ed25519 key generation", file=sys.stderr)
        return 1

    priv_key = ed25519.Ed25519PrivateKey.generate()
    pub_key = priv_key.public_key()

    priv_raw = priv_key.private_bytes_raw().hex()
    pub_raw = pub_key.public_bytes_raw().hex()

    if args.out:
        out_base = Path(args.out)
        out_base.parent.mkdir(parents=True, exist_ok=True)
        priv_path = Path(f"{args.out}.priv.hex")
        pub_path = Path(f"{args.out}.pub.hex")
        priv_path.write_text(priv_raw)
        pub_path.write_text(pub_raw)
        print(f"Saved private key -> {priv_path}")
        print(f"Saved public key  -> {pub_path}")

    print(f"Public Key (hex):  {pub_raw}")
    print(f"Private Key (hex): {priv_raw}")
    return 0


def main():
    parser = argparse.ArgumentParser(description="Zenith Package (.zpk) Tool")
    subparsers = parser.add_subparsers(dest="command", required=True)

    # pack
    p_pack = subparsers.add_parser("pack", help="Assemble .zpk package")
    p_pack.add_argument("--manifest", required=True, help="Path to manifest.json")
    p_pack.add_argument("--jar", help="Path to provider.jar")
    p_pack.add_argument("--dex", help="Path to provider.dex")
    p_pack.add_argument("--settings", help="Path to settings.json")
    p_pack.add_argument("--icon", help="Path to icon.png")
    p_pack.add_argument("--private-key", help="Hex string or file containing 32-byte Ed25519 seed")
    p_pack.add_argument("--prefer-dex", action="store_true", help="Prefer Android Dex for signing")
    p_pack.add_argument("--output", required=True, help="Output .zpk file path")

    # index
    p_index = subparsers.add_parser("index", help="Generate repository index.json")
    p_index.add_argument("--repo-dir", required=True, help="Directory containing .zpk files")
    p_index.add_argument("--output", required=True, help="Output index.json path")
    p_index.add_argument("--base-url", default="", help="Base URL prefix for downloadable artifacts")
    p_index.add_argument("--repo-name", help="Human-readable name of the repository")
    p_index.add_argument("--repo-desc", help="Description of the repository")
    p_index.add_argument("--repo-icon", help="Icon URL for the repository")

    # verify
    p_verify = subparsers.add_parser("verify", help="Verify .zpk package")
    p_verify.add_argument("package", help="Path to .zpk package")

    # keygen
    p_keygen = subparsers.add_parser("keygen", help="Generate Ed25519 keypair")
    p_keygen.add_argument("--out", help="Output path prefix for keys")

    args = parser.parse_args()
    if args.command == "pack":
        return cmd_pack(args)
    elif args.command == "index":
        return cmd_index(args)
    elif args.command == "verify":
        return cmd_verify(args)
    elif args.command == "keygen":
        return cmd_keygen(args)
    return 0


if __name__ == "__main__":
    sys.exit(main() or 0)
