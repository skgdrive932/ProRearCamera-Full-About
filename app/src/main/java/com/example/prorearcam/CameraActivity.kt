@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_camera);

    View shutterBtn = findViewById(R.id.btn_shutter);
    ImageView switchCameraBtn = findViewById(R.id.btn_switch_camera);
    FrameLayout cameraPreview = findViewById(R.id.camera_preview);

    // Shutter button click event
    shutterBtn.setOnClickListener(v -> {
        // Photo capture logic yahan likho (CameraX ya Camera2 API)
    });
}
