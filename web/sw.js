/**
 * DeltaVoice Service Worker
 * Enables PWA features: offline support, caching, and installability
 */

const CACHE_NAME = 'deltavoice-v1';
const ASSETS_TO_CACHE = [
    '/',
    '/index.html',
    '/styles.css',
    '/app.js',
    '/manifest.json',
    '/icon-192.png',
    '/icon-512.png'
];

// Install event - cache assets
self.addEventListener('install', (event) => {
    console.log('Service Worker installing...');
    
    event.waitUntil(
        caches.open(CACHE_NAME)
            .then((cache) => {
                console.log('Caching app assets');
                return cache.addAll(ASSETS_TO_CACHE);
            })
            .then(() => self.skipWaiting())
    );
});

// Activate event - clean up old caches
self.addEventListener('activate', (event) => {
    console.log('Service Worker activating...');
    
    event.waitUntil(
        caches.keys().then((cacheNames) => {
            return Promise.all(
                cacheNames
                    .filter((name) => name !== CACHE_NAME)
                    .map((name) => caches.delete(name))
            );
        }).then(() => self.clients.claim())
    );
});

// Fetch event - network first, cache fallback
self.addEventListener('fetch', (event) => {
    const { request } = event;
    const url = new URL(request.url);
    
    // Skip cross-origin requests (like Supabase API calls)
    if (url.origin !== location.origin) {
        return;
    }
    
    event.respondWith(
        fetch(request)
            .then((response) => {
                // Clone the response for caching
                const responseToCache = response.clone();
                
                caches.open(CACHE_NAME).then((cache) => {
                    cache.put(request, responseToCache);
                });
                
                return response;
            })
            .catch(() => {
                // Fallback to cache
                return caches.match(request).then((cachedResponse) => {
                    if (cachedResponse) {
                        return cachedResponse;
                    }
                    
                    // Return offline page for navigation requests
                    if (request.mode === 'navigate') {
                        return caches.match('/index.html');
                    }
                    
                    return new Response('Offline', { status: 503 });
                });
            })
    );
});

// Push notification handler (for future use)
self.addEventListener('push', (event) => {
    const options = {
        body: event.data?.text() || 'New notification',
        icon: '/icon-192.png',
        badge: '/icon-192.png',
        vibrate: [100, 50, 100]
    };
    
    event.waitUntil(
        self.registration.showNotification('DeltaVoice', options)
    );
});
