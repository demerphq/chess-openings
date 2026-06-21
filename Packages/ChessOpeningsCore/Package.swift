// swift-tools-version: 6.3

import PackageDescription

let package = Package(
    name: "ChessOpeningsCore",
    products: [
        .library(name: "ChessOpeningsCore", targets: ["ChessOpeningsCore"])
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
        .testTarget(
            name: "ChessOpeningsCoreTests",
            dependencies: ["ChessOpeningsCore"]
        )
    ]
)
