module.exports = {
    content: {
        enabled: true,
        content: ['./src/**/*.clj'],
    },
    theme: {
        extend: {
            colors: {
                primary: "#146a8e"
            },
        },
    },
    variants: {
        extend: {},
    },
    plugins: [require("daisyui")],
};
