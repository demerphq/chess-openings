// swift-tools-version: 6.3

import PackageDescription

let package = Package(
    name: "ChessOpeningsCore",
    products: [
        .library(name: "ChessOpeningsCore", targets: ["ChessOpeningsCore"]),
        .library(name: "ChessOpeningsCoreBridge", type: .dynamic, targets: ["ChessOpeningsCoreBridge"]),
    ],
    dependencies: [
        .package(url: "https://github.com/chesskit-app/chesskit-swift", exact: "0.17.0")
    ],
    targets: [
        .target(
            name: "ChessOpeningsCore",
            dependencies: [
                .product(name: "ChessKit", package: "chesskit-swift")
            ]
        ),
        .target(
            name: "ChessOpeningsCoreBridge",
            dependencies: [
                "ChessOpeningsCore",
                "ChessOpeningsCoreBridgeJNI",
            ]
        ),
        .target(
            name: "ChessOpeningsCoreBridgeJNI"
        ),
        .testTarget(
            name: "ChessOpeningsCoreTests",
            dependencies: ["ChessOpeningsCore"]
        )
    ]
)
