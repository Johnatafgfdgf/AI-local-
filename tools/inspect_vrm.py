"""Inspect a supplied GLB without loading executable assets or modifying it."""
import argparse
import hashlib
import json
import math
from pathlib import Path
import struct


def inspect(path):
    data = Path(path).read_bytes()
    if len(data) < 20:
        raise ValueError('Truncated GLB header')
    magic, version, total = struct.unpack_from('<4sII', data)
    if magic != b'glTF' or version != 2 or total != len(data):
        raise ValueError('Invalid GLB 2 header or length')
    size, kind = struct.unpack_from('<II', data, 12)
    if kind != 0x4E4F534A or 20 + size > len(data):
        raise ValueError('Missing or truncated JSON chunk')
    document = json.loads(data[20:20+size])
    vrm = document.get('extensions', {}).get('VRM')
    if vrm is None:
        raise ValueError('This inspector currently supports VRM 0.x only')
    bones = vrm.get('humanoid', {}).get('humanBones', [])
    nodes = document.get('nodes', [])
    names = set()
    for bone in bones:
        if bone['bone'] in names or not 0 <= bone['node'] < len(nodes):
            raise ValueError('Duplicate bone or invalid node reference')
        names.add(bone['bone'])
    for node in nodes:
        for field in ('translation', 'rotation', 'scale', 'matrix'):
            if not all(math.isfinite(x) for x in node.get(field, [])):
                raise ValueError('Non-finite transform')
    meshes = document.get('meshes', [])
    accessors = document.get('accessors', [])
    triangles = 0
    for mesh in meshes:
        for primitive in mesh.get('primitives', []):
            if primitive.get('mode', 4) == 4:
                index = primitive.get('indices', primitive['attributes']['POSITION'])
                triangles += accessors[index]['count'] // 3
    return {
        'sha256': hashlib.sha256(data).hexdigest(), 'bytes': len(data),
        'meta': vrm.get('meta', {}), 'humanoidBones': len(bones),
        'fingerJoints': len([n for n in names if any(f in n.lower() for f in ('thumb','index','middle','ring','little'))]),
        'triangles': triangles, 'nodes': len(nodes), 'meshes': len(meshes),
        'animations': len(document.get('animations', [])),
        'textures': len(document.get('textures', [])),
        'springGroups': len(vrm.get('secondaryAnimation', {}).get('boneGroups', [])),
        'colliderGroups': len(vrm.get('secondaryAnimation', {}).get('colliderGroups', [])),
        'facePresets': [g.get('presetName') for g in vrm.get('blendShapeMaster', {}).get('blendShapeGroups', [])],
        'visualValidation': 'not performed',
    }

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('vrm')
    args = parser.parse_args()
    print(json.dumps(inspect(args.vrm), ensure_ascii=False, indent=2))
