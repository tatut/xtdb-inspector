module.exports = {
    purge: {
        enabled: true,
        content: ['./src/**/*.clj'],
    },
    darkMode: false, // or 'media' or 'class'
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
    plugins: [],
};
