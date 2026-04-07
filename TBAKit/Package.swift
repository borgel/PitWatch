// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "TBAKit",
    platforms: [
        .iOS(.v18),
        .watchOS(.v11)
    ],
    products: [
        .library(name: "TBAKit", targets: ["TBAKit"]),
    ],
    targets: [
        .target(name: "TBAKit"),
        .testTarget(
            name: "TBAKitTests",
            dependencies: ["TBAKit"],
            resources: [.copy("Fixtures")]
        ),
    ]
)
